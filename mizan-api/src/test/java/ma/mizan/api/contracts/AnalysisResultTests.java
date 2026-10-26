package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import org.springframework.jdbc.core.simple.JdbcClient;

/** Story 3.3 / ADR-4 over Kafka, with messages in the analyzer's wire format. */
@SpringBootTest(classes = MizanApiApplication.class)
class AnalysisResultTests {

  static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired JdbcClient jdbc;
  @Autowired AnalysisResultService service;

  UUID contractId;
  UUID analysisId;

  @BeforeEach
  void pendingAnalysis() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    contractId = UUID.randomUUID();
    analysisId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'user-a', 'bail.pdf', 1, 'Article 1.')")
        .param(contractId)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                + " VALUES (?, ?, 'fr', 'PENDING')")
        .params(analysisId, contractId)
        .update();
  }

  @Test
  void completedDeliveredTwiceIsAppliedExactlyOnce() {
    String completed = completed(analysisId, contractId);

    send(AnalysisResults.COMPLETED_TOPIC, completed);
    send(AnalysisResults.COMPLETED_TOPIC, completed);

    await().atMost(WAIT).untilAsserted(() -> assertThat(status()).isEqualTo("COMPLETE"));
    // Let the duplicate be consumed too, then check nothing was applied twice.
    await()
        .pollDelay(Duration.ofSeconds(3))
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(flagCount()).isEqualTo(2));
    Map<String, Object> row =
        jdbc.sql(
                "SELECT summary, model, input_tokens, output_tokens, completed_at, version"
                    + " FROM mizan.analyses WHERE id = ?")
            .param(analysisId)
            .query()
            .singleRow();
    assertThat(row.get("summary")).isEqualTo("Contrat de prestation.");
    assertThat(row.get("model")).isEqualTo("claude-opus-5-5");
    assertThat(row.get("input_tokens")).isEqualTo(1358);
    assertThat(row.get("output_tokens")).isEqualTo(1823);
    assertThat(row.get("completed_at")).isNotNull();
    assertThat((Long) row.get("version")).isEqualTo(1L);
    assertThat(
            jdbc.sql(
                    "SELECT position || ':' || risk_level || ':' || coalesce(suggested_correction,"
                        + " '-') FROM mizan.clause_flags WHERE analysis_id = ? ORDER BY position")
                .param(analysisId)
                .query(String.class)
                .list())
        .containsExactly("0:HIGH:Plafonner à 10 %.", "1:LOW:-");
  }

  @Test
  void failedMarksTheAnalysisFailedAndALateResultCannotOverwriteIt() {
    send(
        AnalysisResults.FAILED_TOPIC,
        """
        {"schemaVersion":1,"analysisId":"%s","contractId":"%s","reason":"model-unavailable"}
        """
            .formatted(analysisId, contractId));
    await().atMost(WAIT).untilAsserted(() -> assertThat(status()).isEqualTo("FAILED"));

    assertThat(
            service.complete(
                new AnalysisResults.Completed(
                    1, analysisId, contractId, "late", java.util.List.of(), "m", 1, 1)))
        .isFalse();

    assertThat(status()).isEqualTo("FAILED");
    assertThat(
            jdbc.sql("SELECT failure_reason FROM mizan.analyses WHERE id = ?")
                .param(analysisId)
                .query(String.class)
                .single())
        .isEqualTo("model-unavailable");
    assertThat(flagCount()).isZero();
  }

  @Test
  void resultForAnUnknownOrDeletedContractIsDroppedAndTheConsumerMovesOn() {
    UUID unknown = UUID.randomUUID();
    send(AnalysisResults.COMPLETED_TOPIC, completed(unknown, contractId));
    // Right analysis, wrong contract: never applied across owners' contracts.
    send(AnalysisResults.COMPLETED_TOPIC, completed(analysisId, UUID.randomUUID()));
    // Unreadable message: skipped by the error handler.
    send(AnalysisResults.COMPLETED_TOPIC, "{not json");
    // The real result after them is still applied: nothing blocks the partition.
    send(AnalysisResults.COMPLETED_TOPIC, completed(analysisId, contractId));

    await().atMost(WAIT).untilAsserted(() -> assertThat(status()).isEqualTo("COMPLETE"));
    assertThat(
            jdbc.sql("SELECT count(*) FROM mizan.analyses WHERE id = ?")
                .param(unknown)
                .query(Long.class)
                .single())
        .isZero();

    jdbc.sql("DELETE FROM mizan.contracts WHERE id = ?").param(contractId).update();
    assertThat(
            service.fail(new AnalysisResults.Failed(1, analysisId, contractId, "invalid-output")))
        .isFalse();
  }

  private String status() {
    return jdbc.sql("SELECT status FROM mizan.analyses WHERE id = ?")
        .param(analysisId)
        .query(String.class)
        .single();
  }

  private long flagCount() {
    return jdbc.sql("SELECT count(*) FROM mizan.clause_flags WHERE analysis_id = ?")
        .param(analysisId)
        .query(Long.class)
        .single();
  }

  /** Exactly what mizan-analyzer publishes (AnalysisCompleted, schema version 1). */
  private static String completed(UUID analysisId, UUID contractId) {
    return """
        {"schemaVersion":1,"analysisId":"%s","contractId":"%s",
         "summary":"Contrat de prestation.",
         "flags":[
           {"clauseText":"Pénalité de 10 %% par jour.","riskLevel":"HIGH",
            "explanation":"Sans plafond.","suggestedCorrection":"Plafonner à 10 %%."},
           {"clauseText":"Paiement à 60 jours.","riskLevel":"LOW",
            "explanation":"Délai légal.","suggestedCorrection":""}],
         "model":"claude-opus-5-5","inputTokens":1358,"outputTokens":1823}
        """
        .formatted(analysisId, contractId);
  }

  private static void send(String topic, String json) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      // One key per test: all its messages share a partition and arrive in order.
      producer.send(new ProducerRecord<>(topic, "result-tests", json));
    }
  }
}
