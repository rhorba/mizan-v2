package ma.mizan.api.quota;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module API of the quota module (Story 5.1): per-user and global daily caps, the spend budget, and
 * token accounting, all in daily_usage (docs/database §3). The day is the Africa/Casablanca day the
 * analysis was requested, so a refund or late tokens land on the day the unit was taken.
 */
@Service
public class DailyUsage {

  /** owner_sub of the per-day global row. */
  static final String GLOBAL = "*";

  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  private final JdbcClient jdbc;
  private final QuotaProperties limits;
  private final BudgetProperties budget;
  private final Clock clock;

  DailyUsage(JdbcClient jdbc, QuotaProperties limits, BudgetProperties budget, Clock clock) {
    this.jdbc = jdbc;
    this.limits = limits;
    this.budget = budget;
    this.clock = clock;
  }

  /** What one user sees today. */
  public record Today(int limit, int used, int remaining, BigDecimal spentUsd) {}

  @Transactional(readOnly = true)
  public Today today(String ownerSub) {
    LocalDate day = today();
    Row user = row(ownerSub, day);
    Row global = row(GLOBAL, day);
    int remaining =
        Math.max(
            0,
            Math.min(
                limits.dailyPerUser() - user.analyses(), limits.dailyGlobal() - global.analyses()));
    return new Today(
        limits.dailyPerUser(),
        user.analyses(),
        remaining,
        budget.usd(user.inputTokens(), user.outputTokens()));
  }

  /** What all users together used today, against the global cap (Story 5.2). */
  public record Global(
      int analyses,
      int cap,
      int remaining,
      long inputTokens,
      long outputTokens,
      BigDecimal spentUsd) {}

  @Transactional(readOnly = true)
  public Global globalToday() {
    Row global = row(GLOBAL, today());
    return new Global(
        global.analyses(),
        limits.dailyGlobal(),
        Math.max(0, limits.dailyGlobal() - global.analyses()),
        global.inputTokens(),
        global.outputTokens(),
        budget.usd(global.inputTokens(), global.outputTokens()));
  }

  /**
   * Cheap refusal before scanning and parsing an upload; {@link #reserve} is the atomic check.
   *
   * @throws QuotaExceededException when a cap or the spend budget is reached
   */
  @Transactional(readOnly = true)
  public void check(String ownerSub) {
    LocalDate day = today();
    Row user = row(ownerSub, day);
    if (user.analyses() >= limits.dailyPerUser()) {
      throw refused(ownerSub, QuotaExceededException.Reason.USER_CAP);
    }
    if (row(GLOBAL, day).analyses() >= limits.dailyGlobal()) {
      throw refused(ownerSub, QuotaExceededException.Reason.GLOBAL_CAP);
    }
    if (budget.usd(user.inputTokens(), user.outputTokens()).compareTo(budget.dailyUsd()) >= 0) {
      throw refused(ownerSub, QuotaExceededException.Reason.BUDGET);
    }
  }

  /**
   * Takes one unit of today's global and per-user caps, atomically: each is a conditional upsert
   * that only increments below its cap. Global first, always, so concurrent requests lock rows in
   * the same order. Must run in the caller's transaction so a later failure gives the unit back.
   *
   * @throws QuotaExceededException when a cap is reached (the transaction must roll back)
   */
  @Transactional
  public void reserve(String ownerSub) {
    LocalDate day = today();
    if (!increment(GLOBAL, day, limits.dailyGlobal())) {
      throw refused(ownerSub, QuotaExceededException.Reason.GLOBAL_CAP);
    }
    if (!increment(ownerSub, day, limits.dailyPerUser())) {
      throw refused(ownerSub, QuotaExceededException.Reason.USER_CAP);
    }
  }

  /** A FAILED analysis gives its unit back (Story 5.1), on the day it was taken. */
  @Transactional
  public void refund(String ownerSub, Instant requestedAt) {
    LocalDate day = dayOf(requestedAt);
    for (String owner : new String[] {GLOBAL, ownerSub}) {
      jdbc.sql(
              "UPDATE daily_usage SET analyses = analyses - 1"
                  + " WHERE owner_sub = ? AND usage_day = ? AND analyses > 0")
          .params(owner, day)
          .update();
    }
  }

  /** Billed tokens of one analysis, completed or failed, added to its request day. */
  @Transactional
  public void recordTokens(
      String ownerSub, Instant requestedAt, long inputTokens, long outputTokens) {
    if (inputTokens <= 0 && outputTokens <= 0) {
      return;
    }
    LocalDate day = dayOf(requestedAt);
    for (String owner : new String[] {GLOBAL, ownerSub}) {
      jdbc.sql(
              "INSERT INTO daily_usage (owner_sub, usage_day, input_tokens, output_tokens)"
                  + " VALUES (?, ?, ?, ?) ON CONFLICT (owner_sub, usage_day) DO UPDATE SET"
                  + " input_tokens = daily_usage.input_tokens + EXCLUDED.input_tokens,"
                  + " output_tokens = daily_usage.output_tokens + EXCLUDED.output_tokens")
          .params(owner, day, Math.max(0, inputTokens), Math.max(0, outputTokens))
          .update();
    }
  }

  private boolean increment(String owner, LocalDate day, int cap) {
    Optional<Integer> after =
        jdbc.sql(
                "INSERT INTO daily_usage (owner_sub, usage_day, analyses) VALUES (?, ?, 1)"
                    + " ON CONFLICT (owner_sub, usage_day) DO UPDATE"
                    + " SET analyses = daily_usage.analyses + 1 WHERE daily_usage.analyses < ?"
                    + " RETURNING analyses")
            .params(owner, day, cap)
            .query(Integer.class)
            .optional();
    return after.isPresent() && after.get() <= cap;
  }

  private Row row(String owner, LocalDate day) {
    return jdbc.sql(
            "SELECT analyses, input_tokens, output_tokens FROM daily_usage"
                + " WHERE owner_sub = ? AND usage_day = ?")
        .params(owner, day)
        .query(
            (rs, n) ->
                new Row(
                    rs.getInt("analyses"), rs.getLong("input_tokens"), rs.getLong("output_tokens")))
        .optional()
        .orElse(Row.EMPTY);
  }

  private QuotaExceededException refused(String ownerSub, QuotaExceededException.Reason reason) {
    AUDIT.info("action=contract.upload.refused subject={} reason={}", ownerSub, reason);
    return new QuotaExceededException(reason);
  }

  /** Today where the caps reset (Africa/Casablanca by default). */
  public LocalDate today() {
    return dayOf(clock.instant());
  }

  public ZoneId zone() {
    return limits.zone();
  }

  /**
   * The calendar day of an instant in the caps' zone, as PostgreSQL sees it. The database decides,
   * not the JVM: JDK tzdata lags (25.0.4 still had Morocco at UTC+1 after its 2026-09-20 switch to
   * UTC+0), and the statistics count days in SQL, so both must use the same rules.
   */
  LocalDate dayOf(Instant instant) {
    return jdbc.sql("SELECT (CAST(? AS timestamptz) AT TIME ZONE ?)::date")
        .params(instant.atOffset(ZoneOffset.UTC), limits.zone().getId())
        .query(LocalDate.class)
        .single();
  }

  private record Row(int analyses, long inputTokens, long outputTokens) {
    static final Row EMPTY = new Row(0, 0, 0);
  }
}
