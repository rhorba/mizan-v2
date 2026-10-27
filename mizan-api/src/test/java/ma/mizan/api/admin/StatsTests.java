package ma.mizan.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import ma.mizan.api.quota.DailyUsage;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Story 5.2: the admin overview over the last 14 Casablanca days. */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = {"mizan.quota.daily-per-user=5", "mizan.quota.daily-global=20"})
@AutoConfigureMockMvc
class StatsTests {

  static final RequestPostProcessor ADMIN =
      jwt().jwt(j -> j.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_admin"));
  static final RequestPostProcessor USER =
      jwt().jwt(j -> j.subject("user-a")).authorities(new SimpleGrantedAuthority("ROLE_user"));

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired DailyUsage daily;

  LocalDate today;
  UUID contractId;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.daily_usage").update();
    today = daily.today();
    contractId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'user-a', 'bail.pdf', 1, 'Secret clause.')")
        .param(contractId)
        .update();
  }

  @Test
  void adminGetsFourteenDaysOfCountsSplitsAndToday() {
    // 00:30 in Casablanca is still yesterday in UTC: it must count for today.
    UUID done = analysis(today, LocalTime.of(0, 30), "COMPLETE");
    flags(done, "HIGH", "HIGH", "LOW");
    analysis(today, LocalTime.of(9, 0), "FAILED");
    analysis(today, LocalTime.of(10, 0), "PENDING");
    flags(analysis(today.minusDays(13), LocalTime.NOON, "COMPLETE"), "MEDIUM");
    // Day -14 is outside the window, with its flags.
    flags(analysis(today.minusDays(14), LocalTime.NOON, "COMPLETE"), "HIGH");
    jdbc.sql(
            "INSERT INTO mizan.daily_usage (owner_sub, usage_day, analyses, input_tokens,"
                + " output_tokens) VALUES ('*', ?, 3, 10000, 2000)")
        .param(today)
        .update();

    MvcTestResult result = stats(ADMIN);

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"from":"%s","to":"%s",
             "statuses":{"PENDING":1,"ANALYZING":0,"COMPLETE":2,"FAILED":1},
             "risks":{"HIGH":2,"MEDIUM":1,"LOW":1},
             "today":{"analyses":3,"cap":20,"remaining":17,
                      "inputTokens":10000,"outputTokens":2000}}
            """
                .formatted(today.minusDays(13), today));
    assertThat(result).bodyJson().extractingPath("$.days.length()").isEqualTo(14);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.days[0]")
        .isEqualTo(
            Map.of(
                "day",
                today.minusDays(13).toString(),
                "statuses",
                Map.of("PENDING", 0, "ANALYZING", 0, "COMPLETE", 1, "FAILED", 0)));
    assertThat(result)
        .bodyJson()
        .extractingPath("$.days[13].statuses")
        .isEqualTo(Map.of("PENDING", 1, "ANALYZING", 0, "COMPLETE", 1, "FAILED", 1));
    assertThat(result).bodyJson().extractingPath("$.days[5].statuses.COMPLETE").isEqualTo(0);
    // One failed out of three finished in the window.
    assertThat(result)
        .bodyJson()
        .extractingPath("$.failureRate")
        .asNumber()
        .satisfies(r -> assertThat(r.doubleValue()).isCloseTo(1.0 / 3, within()));
    // 10 000 in at $4/Mtok + 2 000 out at $20/Mtok.
    assertThat(result)
        .bodyJson()
        .extractingPath("$.today.spentUsd")
        .asNumber()
        .satisfies(usd -> assertThat(usd.doubleValue()).isCloseTo(0.08, within()));
  }

  @Test
  void quietWindowHasZerosAndNoFailureRate() {
    MvcTestResult result = stats(ADMIN);

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"statuses":{"PENDING":0,"ANALYZING":0,"COMPLETE":0,"FAILED":0},
             "failureRate":null,
             "risks":{"HIGH":0,"MEDIUM":0,"LOW":0},
             "today":{"analyses":0,"cap":20,"remaining":20,"inputTokens":0,"outputTokens":0}}
            """);
    assertThat(result).bodyJson().extractingPath("$.days.length()").isEqualTo(14);
  }

  @Test
  void onlyAdminsSeeTheStatistics() {
    assertThat(stats(USER)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/v1/admin/stats").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void neverCarriesTextFileNamesOrOwners() {
    flags(analysis(today, LocalTime.NOON, "COMPLETE"), "HIGH");

    assertThat(stats(ADMIN))
        .bodyText()
        .doesNotContain("Secret clause", "bail.pdf", "user-a", contractId.toString());
  }

  private MvcTestResult stats(RequestPostProcessor who) {
    return mvc.get().uri("/api/v1/admin/stats").with(who).exchange();
  }

  private UUID analysis(LocalDate day, LocalTime time, String status) {
    UUID id = UUID.randomUUID();
    // Local time to instant in SQL: the JDK's Casablanca rules are stale (see DailyUsage#dayOf).
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status, requested_at)"
                + " VALUES (?, ?, 'fr', ?,"
                + " (CAST(? AS date) + CAST(? AS time)) AT TIME ZONE 'Africa/Casablanca')")
        .params(id, contractId, status, day, time)
        .update();
    return id;
  }

  private void flags(UUID analysisId, String... levels) {
    for (int i = 0; i < levels.length; i++) {
      jdbc.sql(
              "INSERT INTO mizan.clause_flags (id, analysis_id, position, clause_text, risk_level,"
                  + " explanation) VALUES (?, ?, ?, 'Secret clause.', ?, 'why')")
          .params(UUID.randomUUID(), analysisId, i, levels[i])
          .update();
    }
  }

  private static Offset<Double> within() {
    return Offset.offset(1e-9);
  }
}
