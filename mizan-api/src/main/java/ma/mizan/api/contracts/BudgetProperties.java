package ma.mizan.api.contracts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Per-user daily model spend (Sprint 3, 🔴), bound from {@code mizan.budget.*}. Prices default to
 * Claude Opus 5.5 list prices; change them with the model.
 *
 * @param dailyUsd what one user's analyses may cost per day; 0 blocks every upload
 * @param inputUsdPerMtok price of one million input tokens
 * @param outputUsdPerMtok price of one million output tokens (thinking included)
 * @param zone where "today" starts
 */
@Validated
@ConfigurationProperties("mizan.budget")
record BudgetProperties(
    @DefaultValue("0.50") @NotNull @PositiveOrZero BigDecimal dailyUsd,
    @DefaultValue("4") @NotNull @PositiveOrZero BigDecimal inputUsdPerMtok,
    @DefaultValue("20") @NotNull @PositiveOrZero BigDecimal outputUsdPerMtok,
    @DefaultValue("Africa/Casablanca") @NotNull ZoneId zone) {}
