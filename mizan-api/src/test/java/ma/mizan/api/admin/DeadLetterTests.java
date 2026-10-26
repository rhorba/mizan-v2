package ma.mizan.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import ma.mizan.api.shared.kafka.KafkaTestcontainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Admin dead-letter list and retry (Sprint 3 🔴). */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class DeadLetterTests {

  static final RequestPostProcessor ADMIN =
      jwt().jwt(j -> j.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_admin"));
  static final RequestPostProcessor USER =
      jwt().jwt(j -> j.subject("user-a")).authorities(new SimpleGrantedAuthority("ROLE_user"));

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  UUID contractId;
  UUID analysisId;

  @BeforeEach
  void failedAnalysisInTheDlt() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.event_publication").update();
    contractId = UUID.randomUUID();
    analysisId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'user-a', 'bail.pdf', 1, 'Article 1. Secret clause.')")
        .param(contractId)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status, failure_reason,"
                + " completed_at) VALUES (?, ?, 'ar', 'FAILED', 'model-unavailable', now())")
        .params(analysisId, contractId)
        .update();
    dlt(
        contractId.toString(),
        """
        {"schemaVersion":1,"analysisId":"%s","contractId":"%s","language":"ar",
         "text":"Article 1. Secret clause.","requestedAt":"2026-10-26T10:00:00Z"}
        """
            .formatted(analysisId, contractId));
  }

  @Test
  void adminSeesTheDeadLetterWithItsStatusButNeverTheContractText() throws Exception {
    MvcTestResult result = mvc.get().uri("/api/v1/admin/dead-letters").with(ADMIN).exchange();

    assertThat(result).hasStatusOk();
    String entry = "$[?(@.analysisId == '%s')].".formatted(analysisId);
    assertThat(result)
        .bodyJson()
        .extractingPath(entry + "contractId")
        .asArray()
        .containsExactly(contractId.toString());
    assertThat(result)
        .bodyJson()
        .extractingPath(entry + "language")
        .asArray()
        .containsExactly("ar");
    assertThat(result)
        .bodyJson()
        .extractingPath(entry + "status")
        .asArray()
        .containsExactly("FAILED");
    assertThat(result)
        .bodyJson()
        .extractingPath(entry + "error")
        .asArray()
        .containsExactly("IllegalStateException: simulated timeout");
    assertThat(result).bodyJson().extractingPath(entry + "text").asArray().isEmpty();
    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain("Secret clause");
  }

  @Test
  void unreadableDeadLetterIsListedWithoutIds() {
    dlt("junk", "{not json");

    assertThat(mvc.get().uri("/api/v1/admin/dead-letters").with(ADMIN).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath(
            "$[?(@.error == 'IllegalStateException: simulated timeout'"
                + " && @.analysisId == null)]")
        .asArray()
        .isNotEmpty();
  }

  @Test
  void usersCannotSeeOrRetryDeadLetters() {
    assertThat(mvc.get().uri("/api/v1/admin/dead-letters").with(USER).exchange())
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(retry(analysisId, USER)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(status()).isEqualTo("FAILED");
  }

  @Test
  void retryResetsTheAnalysisAndRequestsItAgainOnlyOnce() {
    assertThat(retry(analysisId, ADMIN)).hasStatus(HttpStatus.ACCEPTED);

    Map<String, Object> row =
        jdbc.sql("SELECT status, failure_reason, completed_at FROM mizan.analyses WHERE id = ?")
            .param(analysisId)
            .query()
            .singleRow();
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("failure_reason")).isNull();
    assertThat(row.get("completed_at")).isNull();
    // Requested again through the outbox: the publication is created and then completed.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.sql("SELECT count(*) FROM mizan.event_publication")
                            .query(Long.class)
                            .single())
                    .isZero());

    assertThat(retry(analysisId, ADMIN))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:analysis-not-failed");
  }

  @Test
  void deletedContractCannotBeRetried() {
    jdbc.sql("DELETE FROM mizan.contracts WHERE id = ?").param(contractId).update();

    assertThat(retry(analysisId, ADMIN))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:analysis-not-found");
    assertThat(mvc.get().uri("/api/v1/admin/dead-letters").with(ADMIN).exchange())
        .bodyJson()
        .extractingPath("$[?(@.analysisId == '%s')].status".formatted(analysisId))
        .asArray()
        .containsOnlyNulls();
  }

  private MvcTestResult retry(UUID id, RequestPostProcessor who) {
    return mvc.post().uri("/api/v1/admin/analyses/{id}/retry", id).with(who).exchange();
  }

  private String status() {
    return jdbc.sql("SELECT status FROM mizan.analyses WHERE id = ?")
        .param(analysisId)
        .query(String.class)
        .single();
  }

  /** What the analyzer recoverer writes: original value plus kafka_dlt-* headers. */
  private static void dlt(String key, String value) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      ProducerRecord<String, String> record =
          new ProducerRecord<>(DeadLetterReader.DLT, key, value);
      record
          .headers()
          .add(
              KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN,
              "java.lang.IllegalStateException".getBytes(StandardCharsets.UTF_8))
          .add(
              KafkaHeaders.DLT_EXCEPTION_MESSAGE,
              "simulated timeout".getBytes(StandardCharsets.UTF_8));
      producer.send(record);
    }
  }
}
