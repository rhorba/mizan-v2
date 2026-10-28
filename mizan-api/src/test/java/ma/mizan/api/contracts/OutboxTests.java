package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import ma.mizan.api.shared.kafka.KafkaTestcontainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 3.1 / ADR-2: one request message per upload, and nothing lost when Kafka is away (at least
 * once).
 */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class OutboxTests {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired IncompleteEventPublications incomplete;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.event_publication").update();
  }

  @Test
  void uploadPublishesExactlyOneRequestKeyedByContractId() {
    String contractId = uploadContract();

    List<ConsumerRecord<String, String>> records = recordsFor(contractId, 1);

    assertThat(records).hasSize(1);
    ConsumerRecord<String, String> record = records.getFirst();
    // No Java type headers on the wire: the analyzer maps JSON to its own record. The only
    // header is the W3C trace context (Story 6.1), so the analyzer continues the upload's trace.
    assertThat(record.headers().toArray()).extracting(Header::key).containsExactly("traceparent");
    JsonNode body = JSON.readTree(record.value());
    assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(body.get("contractId").asString()).isEqualTo(contractId);
    assertThat(body.get("language").asString()).isEqualTo("fr");
    assertThat(body.get("text").asString()).contains("Article 1.");
    String analysisId =
        jdbc.sql("SELECT id::text FROM mizan.analyses WHERE contract_id = ?::uuid")
            .param(contractId)
            .query(String.class)
            .single();
    assertThat(body.get("analysisId").asString()).isEqualTo(analysisId);
    assertThat(body.get("requestedAt").isString()).isTrue();
    // completion-mode: delete — no copy of the contract text stays in the registry.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(publicationCount()).isZero());
  }

  @Test
  void requestCommittedWhileKafkaIsDownIsNotLostAndIsPublishedWhenResubmitted() {
    var kafka = KafkaTestcontainer.container();
    kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    String contractId;
    try {
      contractId = uploadContract();
      // The upload committed, but the send failed: the request is kept, not lost.
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  assertThat(
                          jdbc.sql(
                                  "SELECT count(*) FROM mizan.event_publication"
                                      + " WHERE completion_date IS NULL AND status = 'FAILED'")
                              .query(Long.class)
                              .single())
                      .isEqualTo(1));
    } finally {
      kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }

    // What a restart does (republish-outstanding-events-on-restart) and the sweep does live.
    incomplete.resubmitIncompletePublications(publication -> true);

    // Never lost, but not exactly once: a send that timed out may still have reached the broker
    // (it was buffered while paused), so the resend can duplicate it. ADR-4 consumers are
    // idempotent; every copy names the same analysis.
    List<ConsumerRecord<String, String>> records = recordsFor(contractId, 1);
    assertThat(records).isNotEmpty();
    assertThat(records)
        .extracting(r -> JSON.readTree(r.value()).get("analysisId").asString())
        .containsOnly(
            jdbc.sql("SELECT id::text FROM mizan.analyses WHERE contract_id = ?::uuid")
                .param(contractId)
                .query(String.class)
                .single());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(publicationCount()).isZero());
  }

  private long publicationCount() {
    return jdbc.sql("SELECT count(*) FROM mizan.event_publication").query(Long.class).single();
  }

  private String uploadContract() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/contracts")
            .multipart()
            .file(
                new MockMultipartFile(
                    "file", "bail.pdf", "application/pdf", PdfFixtures.textPdf(2)))
            .param("language", "fr")
            .with(
                jwt()
                    .jwt(j -> j.subject("user-a"))
                    .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.ACCEPTED);
    return result.getResponse().getHeader("Location").replace("/api/v1/contracts/", "");
  }

  /**
   * Reads the whole topic with a fresh group until {@code expected} records for this key arrive,
   * then keeps reading briefly so a duplicate would be caught too.
   */
  private static List<ConsumerRecord<String, String>> recordsFor(String key, int expected) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "outbox-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    List<ConsumerRecord<String, String>> matching = new ArrayList<>();
    try (var consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(AnalysisRequested.TOPIC));
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      long settleUntil = Long.MAX_VALUE;
      while (System.nanoTime() < Math.min(deadline, settleUntil)) {
        for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
          if (key.equals(r.key())) {
            matching.add(r);
          }
        }
        if (matching.size() >= expected && settleUntil == Long.MAX_VALUE) {
          settleUntil = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        }
      }
    }
    return matching;
  }
}
