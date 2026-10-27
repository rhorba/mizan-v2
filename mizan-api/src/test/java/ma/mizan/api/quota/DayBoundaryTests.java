package ma.mizan.api.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Which day a quota unit belongs to is decided by PostgreSQL, not the JVM (.logs/issues.md
 * 2026-10-27). Morocco moved to permanent UTC+0 on 2026-09-20 (IANA tzdata 2026c); JDK 25.0.3 (tzdb
 * 2026a) and 25.0.4 (2026b) still say UTC+1 and would put 23:30 UTC on the next day.
 */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = {"mizan.quota.daily-per-user=5", "mizan.quota.daily-global=20"})
class DayBoundaryTests {

  @Autowired DailyUsage usage;

  @Test
  void lateEveningUtcIsStillTheSameDayInCasablancaAfterTheSwitchToUtcPlusZero() {
    assertThat(usage.dayOf(Instant.parse("2026-10-26T23:30:00Z")))
        .isEqualTo(LocalDate.parse("2026-10-26"));
    assertThat(usage.dayOf(Instant.parse("2026-10-27T00:00:00Z")))
        .isEqualTo(LocalDate.parse("2026-10-27"));
  }

  @Test
  void beforeTheSwitchCasablancaWasUtcPlusOne() {
    assertThat(usage.dayOf(Instant.parse("2026-06-10T23:30:00Z")))
        .isEqualTo(LocalDate.parse("2026-06-11"));
  }
}
