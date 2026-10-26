package ma.mizan.api.quota;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Daily caps (FR-8), bound from {@code mizan.quota.*}.
 *
 * @param dailyPerUser analyses one user may run per day
 * @param dailyGlobal analyses all users together may run per day
 * @param zone where "today" starts (also for the spend budget)
 */
@Validated
@ConfigurationProperties("mizan.quota")
record QuotaProperties(
    @DefaultValue("5") @Positive int dailyPerUser,
    @DefaultValue("20") @Positive int dailyGlobal,
    @DefaultValue("Africa/Casablanca") @NotNull ZoneId zone) {}
