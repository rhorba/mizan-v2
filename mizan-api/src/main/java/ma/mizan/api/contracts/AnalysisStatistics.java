package ma.mizan.api.contracts;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module API for the admin statistics (Story 5.2): counts only, never text or owners. Built from
 * the analyses that still exist, so a deleted contract leaves these numbers (daily_usage keeps the
 * billed units and tokens).
 */
@Service
public class AnalysisStatistics {

  /**
   * requested_at within [start of day ?, start of day ? + 1), each day in zone ?; uses the index.
   */
  private static final String WINDOW =
      "requested_at >= CAST(? AS date)::timestamp AT TIME ZONE ?"
          + " AND requested_at < (CAST(? AS date) + 1)::timestamp AT TIME ZONE ?";

  private final JdbcClient jdbc;

  AnalysisStatistics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Analyses requested on one day, by status; every status is present. */
  public record Day(LocalDate day, Map<AnalysisStatus, Long> statuses) {}

  /** Every day of the window in order, and the flags of its analyses by risk level. */
  public record Window(List<Day> days, Map<RiskLevel, Long> risks) {}

  /**
   * Days {@code from} to {@code to} inclusive, as calendar days in {@code zone}. Day boundaries are
   * computed by PostgreSQL, like the quota days (JDK tzdata can lag; see DailyUsage#dayOf).
   */
  @Transactional(readOnly = true)
  public Window between(LocalDate from, LocalDate to, ZoneId zone) {
    String tz = zone.getId();
    Map<LocalDate, Map<AnalysisStatus, Long>> byDay = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      byDay.put(d, zeros(AnalysisStatus.class));
    }
    jdbc.sql(
            "SELECT (requested_at AT TIME ZONE ?)::date AS day, status, count(*) AS n"
                + " FROM analyses WHERE "
                + WINDOW
                + " GROUP BY 1, 2")
        .params(tz, from, tz, to, tz)
        .query(
            (rs, n) ->
                new Count<>(
                    rs.getObject("day", LocalDate.class),
                    AnalysisStatus.valueOf(rs.getString("status")),
                    rs.getLong("n")))
        .list()
        .forEach(c -> byDay.get(c.day()).put(c.key(), c.n()));

    Map<RiskLevel, Long> risks = zeros(RiskLevel.class);
    jdbc.sql(
            "SELECT f.risk_level, count(*) AS n FROM clause_flags f"
                + " JOIN analyses a ON a.id = f.analysis_id"
                + " WHERE "
                + WINDOW.replace("requested_at", "a.requested_at")
                + " GROUP BY 1")
        .params(from, tz, to, tz)
        .query(
            (rs, n) ->
                new Count<>(null, RiskLevel.valueOf(rs.getString("risk_level")), rs.getLong("n")))
        .list()
        .forEach(c -> risks.put(c.key(), c.n()));

    List<Day> days = new ArrayList<>();
    byDay.forEach((day, statuses) -> days.add(new Day(day, statuses)));
    return new Window(days, risks);
  }

  private record Count<K>(LocalDate day, K key, long n) {}

  private static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
    Map<E, Long> map = new EnumMap<>(type);
    for (E e : type.getEnumConstants()) {
      map.put(e, 0L);
    }
    return map;
  }
}
