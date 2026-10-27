package ma.mizan.api.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ma.mizan.api.contracts.AnalysisStatistics;
import ma.mizan.api.contracts.AnalysisStatus;
import ma.mizan.api.contracts.RiskLevel;
import ma.mizan.api.quota.DailyUsage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 5.2: the admin overview — analyses per day over 14 days, status split, failure rate, risk
 * split, and today's tokens and global cap. Counts only. Role {@code admin} is enforced for
 * /api/v1/admin/** by SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
class StatsController {

  static final int DAYS = 14;

  private final AnalysisStatistics statistics;
  private final DailyUsage usage;

  StatsController(AnalysisStatistics statistics, DailyUsage usage) {
    this.statistics = statistics;
    this.usage = usage;
  }

  record DayView(LocalDate day, Map<AnalysisStatus, Long> statuses) {}

  record TodayView(
      int analyses,
      int cap,
      int remaining,
      long inputTokens,
      long outputTokens,
      BigDecimal spentUsd) {}

  /**
   * @param failureRate FAILED over finished (COMPLETE + FAILED) analyses in the window, null when
   *     none finished
   */
  record StatsView(
      LocalDate from,
      LocalDate to,
      List<DayView> days,
      Map<AnalysisStatus, Long> statuses,
      Double failureRate,
      Map<RiskLevel, Long> risks,
      TodayView today) {}

  @GetMapping
  StatsView stats() {
    LocalDate to = usage.today();
    LocalDate from = to.minusDays(DAYS - 1);
    AnalysisStatistics.Window window = statistics.between(from, to, usage.zone());

    Map<AnalysisStatus, Long> totals = new EnumMap<>(AnalysisStatus.class);
    window.days().forEach(d -> d.statuses().forEach((s, n) -> totals.merge(s, n, Long::sum)));
    long failed = totals.getOrDefault(AnalysisStatus.FAILED, 0L);
    long finished = failed + totals.getOrDefault(AnalysisStatus.COMPLETE, 0L);

    DailyUsage.Global global = usage.globalToday();
    return new StatsView(
        from,
        to,
        window.days().stream().map(d -> new DayView(d.day(), d.statuses())).toList(),
        totals,
        finished == 0 ? null : (double) failed / finished,
        window.risks(),
        new TodayView(
            global.analyses(),
            global.cap(),
            global.remaining(),
            global.inputTokens(),
            global.outputTokens(),
            global.spentUsd()));
  }
}
