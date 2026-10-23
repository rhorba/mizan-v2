package ma.mizan.api.quota;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Daily caps (FR-8), bound from {@code mizan.quota.*}.
 *
 * @param dailyPerUser analyses one user may run per day
 */
@Validated
@ConfigurationProperties("mizan.quota")
record QuotaProperties(@DefaultValue("3") @Positive int dailyPerUser) {}
