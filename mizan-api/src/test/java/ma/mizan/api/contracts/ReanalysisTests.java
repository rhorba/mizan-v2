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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Story 4.3: a new analysis of an owned contract in another language, without re-uploading. */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = {"mizan.quota.daily-per-user=5", "mizan.quota.daily-global=20"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class ReanalysisTests {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ApplicationEvents events;
  @Autowired DailyUsage daily;

  UUID contractId;

  @BeforeEach
  void aliceHasAContractAnalyzedInFrench() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.daily_usage").update();
    contractId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'alice', 'bail.pdf', 1, 'Article 1. Le loyer est payable.')")
        .param(contractId)
        .update();
    analysis("fr", "COMPLETE");
  }

  @Test
  void analyzingInArabicStartsANewAnalysisUsesAUnitAndKeepsTheFrenchOne() {
    MvcTestResult result = reanalyze("alice", "ar");

    assertThat(result)
        .hasStatus(HttpStatus.ACCEPTED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"language":"ar","status":"PENDING"}
            """);
    UUID created =
        jdbc.sql("SELECT id FROM mizan.analyses WHERE language = 'ar'").query(UUID.class).single();
    assertThat(result.getResponse().getHeader("Location"))
        .isEqualTo("/api/v1/contracts/" + contractId + "/analyses/" + created);
    assertThat(usage("alice")).isEqualTo(1);
    assertThat(usage("*")).isEqualTo(1);

    // Requested through the outbox from the stored text (ADR-5): no re-upload.
    assertThat(events.stream(AnalysisRequested.class))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.analysisId()).isEqualTo(created);
              assertThat(r.contractId()).isEqualTo(contractId);
              assertThat(r.language()).isEqualTo("ar");
              assertThat(r.text()).isEqualTo("Article 1. Le loyer est payable.");
            });

    // Both are listed for the tabs, newest first; the latest drives the live status.
    assertThat(get("alice"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"analysis":{"id":"%s","language":"ar"},
             "analyses":[{"id":"%s","language":"ar","status":"PENDING"},
                         {"language":"fr","status":"COMPLETE"}]}
            """
                .formatted(created, created));
  }

  @Test
  void anotherAnalysisStillRunningIsAConflictAndUsesNoUnit() {
    analysis("ar", "ANALYZING");

    assertThat(reanalyze("alice", "ary"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:analysis-in-progress");
    assertThat(usage("alice")).isZero();
    assertThat(analysisCount()).isEqualTo(2);
  }

  @Test
  void aLanguageThatAlreadyHasAResultIsAConflict() {
    assertThat(reanalyze("alice", "fr"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:already-analyzed");
    assertThat(usage("alice")).isZero();
  }

  @Test
  void aLanguageWhoseAnalysisFailedCanBeRequestedAgain() {
    analysis("ar", "FAILED");

    assertThat(reanalyze("alice", "ar")).hasStatus(HttpStatus.ACCEPTED);
    assertThat(analysisCount()).isEqualTo(3);
  }

  @Test
  void anUnknownLanguageIsRefused() {
    assertThat(reanalyze("alice", "en"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:bad-language");
  }

  @Test
  void aUserAtTheCapGetsTheQuotaProblemAndNoAnalysis() {
    jdbc.sql("INSERT INTO mizan.daily_usage (owner_sub, usage_day, analyses) VALUES (?, ?, 5)")
        .params("alice", daily.today())
        .update();

    assertThat(reanalyze("alice", "ar"))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:quota-exceeded");
    assertThat(analysisCount()).isEqualTo(1);
    assertThat(events.stream(AnalysisRequested.class)).isEmpty();
  }

  @Test
  void parallelRequestsStartExactlyOneAnalysis() throws Exception {
    List<Integer> statuses = new ArrayList<>();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Integer>> calls = new ArrayList<>();
      for (String language : List.of("ar", "ary", "ar", "ary", "ar")) {
        calls.add(
            pool.submit(
                () -> {
                  start.await();
                  return reanalyze("alice", language).getResponse().getStatus();
                }));
      }
      start.countDown();
      for (Future<Integer> call : calls) {
        statuses.add(call.get());
      }
    }

    assertThat(statuses).filteredOn(s -> s == 202).hasSize(1);
    assertThat(statuses).filteredOn(s -> s == 409).hasSize(4);
    assertThat(analysisCount()).isEqualTo(2);
    assertThat(usage("alice")).isEqualTo(1);
  }

  private void analysis(String language, String status) {
    jdbc.sql("INSERT INTO mizan.analyses (id, contract_id, language, status) VALUES (?, ?, ?, ?)")
        .params(UUID.randomUUID(), contractId, language, status)
        .update();
  }

  private long analysisCount() {
    return jdbc.sql("SELECT count(*) FROM mizan.analyses").query(Long.class).single();
  }

  private int usage(String owner) {
    return jdbc.sql("SELECT coalesce(sum(analyses), 0) FROM mizan.daily_usage WHERE owner_sub = ?")
        .param(owner)
        .query(Integer.class)
        .single();
  }

  private MvcTestResult reanalyze(String subject, String language) {
    return mvc.post()
        .uri("/api/v1/contracts/{id}/analyses", contractId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"language\":\"" + language + "\"}")
        .with(user(subject))
        .exchange();
  }

  private MvcTestResult get(String subject) {
    return mvc.get().uri("/api/v1/contracts/{id}", contractId).with(user(subject)).exchange();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor user(
      String subject) {
    return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user"));
  }
}
