package ma.mizan.api.contracts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Refuses a new analysis once the user's analyses today have cost {@code mizan.budget.daily-usd}
 * (token counts saved by Story 3.3 × configured prices). Checked before any model call is
 * requested; the organisation's monthly cap in the Claude Console stays the hard ceiling.
 *
 * <p>Known gap: deleting contracts deletes their analyses and so lowers today's spend. Spend that
 * survives deletion arrives with daily_usage (Story 5.1).
 */
@Component
class SpendBudget {

  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  private final AnalysisRepository analyses;
  private final BudgetProperties budget;
  private final Clock clock;

  SpendBudget(AnalysisRepository analyses, BudgetProperties budget, Clock clock) {
    this.analyses = analyses;
    this.budget = budget;
    this.clock = clock;
  }

  void check(String ownerSub) {
    BigDecimal spent = spentToday(ownerSub);
    if (spent.compareTo(budget.dailyUsd()) >= 0) {
      AUDIT.info(
          "action=contract.upload.refused subject={} reason=budget-exceeded spentUsd={}",
          ownerSub,
          spent);
      throw new UploadRejectedException(UploadProblem.BUDGET_EXCEEDED);
    }
  }

  BigDecimal spentToday(String ownerSub) {
    Instant startOfDay =
        LocalDate.now(clock.withZone(budget.zone())).atStartOfDay(budget.zone()).toInstant();
    return analyses.spendSince(ownerSub, startOfDay).usd(budget);
  }
}
