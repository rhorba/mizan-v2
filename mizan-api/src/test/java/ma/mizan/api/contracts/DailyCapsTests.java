package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.mizan.api.MizanApiApplication;
import ma.mizan.api.quota.DailyUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Story 5.1 with the real caps (5 per user, 20 global) and the spend budget, over HTTP. */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = {"mizan.quota.daily-per-user=5", "mizan.quota.daily-global=20"})
@AutoConfigureMockMvc
class DailyCapsTests {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired AnalysisResultService results;
  @Autowired DailyUsage daily;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.daily_usage").update();
  }

  @Test
  void quotaStartsFullAndEachAcceptedUploadUsesOneUnit() {
    assertThat(quota("user-a"))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"limit":5,"used":0,"remaining":5}
            """);

    assertThat(upload("user-a")).hasStatus(HttpStatus.ACCEPTED);

    assertThat(quota("user-a"))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"limit":5,"used":1,"remaining":4}
            """);
    assertThat(usage("*")).isEqualTo(1);
  }

  @Test
  void aUserAtFourSendingTenParallelUploadsGetsExactlyOneAccepted() throws Exception {
    setUsage("user-a", 4);

    List<Integer> statuses = new ArrayList<>();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        calls.add(
            pool.submit(
                () -> {
                  start.await();
                  return upload("user-a").getResponse().getStatus();
                }));
      }
      start.countDown();
      for (Future<Integer> call : calls) {
        statuses.add(call.get());
      }
    }

    assertThat(statuses).filteredOn(s -> s == 202).hasSize(1);
    assertThat(statuses).filteredOn(s -> s == 429).hasSize(9);
    assertThat(usage("user-a")).isEqualTo(5);
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.contracts").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  void userAtTheCapIsRefusedWithAQuotaProblem() {
    setUsage("user-a", 5);

    assertThat(upload("user-a"))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:quota-exceeded");
    assertThat(quota("user-a")).bodyJson().extractingPath("$.remaining").isEqualTo(0);
  }

  @Test
  void globalCapStopsEveryoneAndQuotaShowsIt() {
    setUsage("*", 20);

    assertThat(upload("user-b"))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:global-quota-exceeded");
    assertThat(quota("user-b"))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"limit":5,"used":0,"remaining":0}
            """);
  }

  @Test
  void aRefusedFileDoesNotUseAUnit() {
    MvcTestResult refused =
        mvc.post()
            .uri("/api/v1/contracts")
            .multipart()
            .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf".getBytes()))
            .param("language", "fr")
            .with(user("user-a"))
            .exchange();

    assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(usage("user-a")).isZero();
    assertThat(usage("*")).isZero();
  }

  @Test
  void failedAnalysisRefundsItsUnitButItsTokensStillCount() {
    assertThat(upload("user-a")).hasStatus(HttpStatus.ACCEPTED);
    UUID analysisId = jdbc.sql("SELECT id FROM mizan.analyses").query(UUID.class).single();
    UUID contractId = jdbc.sql("SELECT id FROM mizan.contracts").query(UUID.class).single();

    assertThat(
            results.fail(
                new AnalysisResults.Failed(
                    2, analysisId, contractId, "invalid-output", 2400, 3000)))
        .isTrue();

    assertThat(usage("user-a")).isZero();
    assertThat(usage("*")).isZero();
    assertThat(tokens("user-a")).containsExactly(2400L, 3000L);
    assertThat(tokens("*")).containsExactly(2400L, 3000L);
  }

  @Test
  void spendBudgetSurvivesDeletingTheContracts() {
    assertThat(upload("user-a")).hasStatus(HttpStatus.ACCEPTED);
    UUID analysisId = jdbc.sql("SELECT id FROM mizan.analyses").query(UUID.class).single();
    UUID contractId = jdbc.sql("SELECT id FROM mizan.contracts").query(UUID.class).single();
    // 25 000 in + 20 000 out at Opus prices = $0.50, the default budget.
    results.complete(
        new AnalysisResults.Completed(
            1, analysisId, contractId, "Résumé", List.of(), "claude-opus-5-5", 25_000, 20_000));
    jdbc.sql("DELETE FROM mizan.contracts").update();

    assertThat(upload("user-a"))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:budget-exceeded");
    assertThat(upload("user-b")).hasStatus(HttpStatus.ACCEPTED);
  }

  private void setUsage(String owner, int analyses) {
    jdbc.sql("INSERT INTO mizan.daily_usage (owner_sub, usage_day, analyses) VALUES (?, ?, ?)")
        .params(owner, daily.today(), analyses)
        .update();
  }

  private int usage(String owner) {
    return jdbc.sql("SELECT coalesce(sum(analyses), 0) FROM mizan.daily_usage WHERE owner_sub = ?")
        .param(owner)
        .query(Integer.class)
        .single();
  }

  private List<Long> tokens(String owner) {
    return jdbc.sql("SELECT input_tokens, output_tokens FROM mizan.daily_usage WHERE owner_sub = ?")
        .param(owner)
        .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2)))
        .single();
  }

  private MvcTestResult quota(String subject) {
    return mvc.get().uri("/api/v1/me/quota").with(user(subject)).exchange();
  }

  private MvcTestResult upload(String subject) {
    return mvc.post()
        .uri("/api/v1/contracts")
        .multipart()
        .file(new MockMultipartFile("file", "bail.pdf", "application/pdf", PdfFixtures.textPdf(1)))
        .param("language", "fr")
        .with(user(subject))
        .exchange();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor user(
      String subject) {
    return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user"));
  }
}
