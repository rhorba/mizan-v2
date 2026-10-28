package ma.mizan.api.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Story 6.1: what the cost and latency alerts read (docs/devops §6). */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class QuotaMetricsTests {

  @Autowired MockMvcTester mvc;
  @Autowired MeterRegistry meters;
  @Autowired JdbcClient jdbc;
  @Autowired DailyUsage usage;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.daily_usage").update();
  }

  @Test
  void globalCapUseIsAGaugeReadFromDailyUsage() {
    assertThat(meters.get("mizan.quota.global.used").gauge().value()).isZero();

    usage.reserve("user-a");
    usage.reserve("user-b");

    assertThat(meters.get("mizan.quota.global.used").gauge().value()).isEqualTo(2);
    assertThat(meters.get("mizan.quota.global.cap").gauge().value()).isEqualTo(1000); // test caps
  }

  @Test
  void requestLatencyHasTheOneSecondBucketTheP95AlertUses() {
    assertThat(
            mvc.get()
                .uri("/api/v1/me/quota")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user-a"))
                        .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .hasStatus(HttpStatus.OK);

    CountAtBucket[] buckets =
        meters
            .get("http.server.requests")
            .tag("uri", "/api/v1/me/quota")
            .timer()
            .takeSnapshot()
            .histogramCounts();
    assertThat(Arrays.stream(buckets).map(b -> b.bucket(TimeUnit.SECONDS)))
        .containsExactly(0.1, 0.25, 0.5, 1.0, 2.5, 5.0);
  }
}
