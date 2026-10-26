package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Story 3.2 end to end over Kafka, with the stub model. */
@SpringBootTest(
    properties = {
      "spring.kafka.listener.auto-startup=true",
      "spring.kafka.consumer.group-id=analysis-flow-tests"
    })
@ActiveProfiles("test")
class AnalysisFlowTests {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @TestConfiguration
  static class StubModel {
    @Bean
    @Primary
    ScriptedChatModel scriptedChatModel() {
      return new ScriptedChatModel();
    }
  }

  @Autowired ScriptedChatModel model;
  @Autowired ChatModel chatModel;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  @Test
  void requestIsAnalyzedOnceAndCompletedIsPublishedWithModelAndTokens() {
    assertThat(chatModel).isSameAs(model);
    UUID analysisId = UUID.randomUUID();
    UUID contractId = UUID.randomUUID();

    send(contractId, request(analysisId, contractId, "fr"));

    JsonNode completed = single(AnalysisCompleted.TOPIC, contractId);
    assertThat(completed.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(completed.get("analysisId").asString()).isEqualTo(analysisId.toString());
    assertThat(completed.get("summary").asString()).isNotBlank();
    assertThat(completed.get("flags").get(0).get("riskLevel").asString()).isEqualTo("HIGH");
    assertThat(completed.get("model").asString()).isEqualTo(ScriptedChatModel.MODEL);
    assertThat(completed.get("inputTokens").asInt()).isEqualTo(1000);
    assertThat(completed.get("outputTokens").asInt()).isEqualTo(200);
    assertThat(model.prompts()).hasSize(1);
    assertThat(records(AnalysisFailed.TOPIC, contractId, Duration.ofSeconds(1))).isEmpty();
  }

  @Test
  void invalidOutputTwicePublishesFailedAndSendsTheRequestToTheDlt() {
    model.otherwise(p -> ScriptedChatModel.answer("not json"));
    UUID analysisId = UUID.randomUUID();
    UUID contractId = UUID.randomUUID();

    send(contractId, request(analysisId, contractId, "ar"));

    JsonNode failed = single(AnalysisFailed.TOPIC, contractId);
    assertThat(failed.get("analysisId").asString()).isEqualTo(analysisId.toString());
    assertThat(failed.get("reason").asString()).isEqualTo("invalid-output");
    // Schema v2: both unusable attempts were billed (1000 in / 200 out each).
    assertThat(failed.get("schemaVersion").asInt()).isEqualTo(2);
    assertThat(failed.get("inputTokens").asInt()).isEqualTo(2000);
    assertThat(failed.get("outputTokens").asInt()).isEqualTo(400);
    assertThat(single(AnalysisRequest.DLT, contractId).get("analysisId").asString())
        .isEqualTo(analysisId.toString());
    // Not retried by Kafka: only the analyzer's own two attempts.
    assertThat(model.prompts()).hasSize(ContractAnalyzer.ATTEMPTS);
    assertThat(records(AnalysisCompleted.TOPIC, contractId, Duration.ofSeconds(1))).isEmpty();
  }

  @Test
  void modelOutageIsRetriedThreeTimesThenFailsToTheDlt() {
    model.otherwise(
        p -> {
          throw new IllegalStateException("simulated timeout");
        });
    UUID contractId = UUID.randomUUID();

    send(contractId, request(UUID.randomUUID(), contractId, "ary"));

    JsonNode failed = single(AnalysisFailed.TOPIC, contractId);
    assertThat(failed.get("reason").asString()).isEqualTo("model-unavailable");
    assertThat(failed.get("inputTokens").asInt()).isZero(); // outages bill nothing
    assertThat(single(AnalysisRequest.DLT, contractId)).isNotNull();
    assertThat(model.prompts()).hasSize(1 + AnalyzerKafkaConfig.RETRIES);
  }

  @Test
  void unreadableMessageGoesToTheDltWithoutCallingTheModel() {
    UUID contractId = UUID.randomUUID();

    send(contractId, "{not json");

    List<ConsumerRecord<String, String>> dlt =
        records(AnalysisRequest.DLT, contractId, Duration.ofSeconds(30));
    assertThat(dlt).singleElement().extracting(ConsumerRecord::value).isEqualTo("{not json");
    assertThat(model.prompts()).isEmpty();
  }

  private static String request(UUID analysisId, UUID contractId, String language) {
    return JSON.writeValueAsString(
        new AnalysisRequest(
            1, analysisId, contractId, language, "Article 1. Pénalité de 10 %.", Instant.now()));
  }

  private static void send(UUID key, String json) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      producer.send(new ProducerRecord<>(AnalysisRequest.TOPIC, key.toString(), json));
    }
  }

  private static JsonNode single(String topic, UUID key) {
    List<ConsumerRecord<String, String>> found = records(topic, key, Duration.ofSeconds(30));
    assertThat(found).as("records on %s", topic).hasSize(1);
    // DLT records carry kafka_dlt-* diagnostics; no record may carry a Java class name.
    assertThat(found.getFirst().headers().lastHeader("__TypeId__")).as("type header").isNull();
    return JSON.readTree(found.getFirst().value());
  }

  /** Reads the topic until a record for this key arrives, then 2 s more to catch duplicates. */
  static List<ConsumerRecord<String, String>> records(String topic, UUID key, Duration wait) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "flow-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    List<ConsumerRecord<String, String>> matching = new ArrayList<>();
    try (var consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + wait.toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(250))) {
          if (key.toString().equals(r.key())) {
            matching.add(r);
          }
        }
        if (!matching.isEmpty()) {
          deadline = Math.min(deadline, System.nanoTime() + Duration.ofSeconds(2).toNanos());
        }
      }
    }
    return matching;
  }
}
