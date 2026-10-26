package ma.mizan.api.contracts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tokens one user's analyses used since a point in time. */
record TokenSpend(long inputTokens, long outputTokens) {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  BigDecimal usd(BudgetProperties prices) {
    return prices
        .inputUsdPerMtok()
        .multiply(BigDecimal.valueOf(inputTokens))
        .add(prices.outputUsdPerMtok().multiply(BigDecimal.valueOf(outputTokens)))
        .divide(MILLION, 6, RoundingMode.HALF_UP);
  }
}
