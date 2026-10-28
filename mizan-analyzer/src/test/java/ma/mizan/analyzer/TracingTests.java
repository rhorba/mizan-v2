package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 6.1 in the analyzer: the trace started by the upload continues from the request's
 * traceparent header through the analysis into the result message, and no span or log record
 * carries the contract text, the prompt or the model's answer.
 */
@SpringBootTest(
    properties = {
      "spring.kafka.listener.auto-startup=true",
      "spring.kafka.consumer.group-id=tracing-tests",
      "management.opentelemetry.tracing.export.schedule-delay=50ms",
      "management.opentelemetry.logging.export.schedule-delay=50ms"
    })
@ActiveProfiles("test")
class TracingTests {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Contract text that must never reach a span or a log record. */
  static final String CONTRACT_TEXT = "Clause secrete de penalite illimitee";

  @TestConfiguration
  static class Setup {
    @Bean
    @Primary
    ScriptedChatModel scriptedChatModel() {
      return new ScriptedChatModel();
    }

    @Bean
    InMemorySpanExporter spans() {
      return InMemorySpanExporter.create();
    }

    @Bean
    InMemoryLogRecordExporter logs() {
      return InMemoryLogRecordExporter.create();
    }
  }

  @Autowired ScriptedChatModel model;
  @Autowired InMemorySpanExporter spans;
  @Autowired InMemoryLogRecordExporter logs;

  @BeforeEach
  void reset() {
    model.reset();
    spans.reset();
    logs.reset();
  }

  @Test
  void analysisContinuesTheUploadTraceIntoTheResultWithoutContractText() {
    String traceId = "0af7651916cd43dd8448eb211c80319c";
    UUID analysisId = UUID.randomUUID();
    UUID contractId = UUID.randomUUID();
    String request =
        JSON.writeValueAsString(
            new AnalysisRequest(1, analysisId, contractId, "fr", CONTRACT_TEXT, Instant.now()));

    send(contractId, request, "00-" + traceId + "-b7ad6b7169203331-01");

    // The result the API applies carries the same trace.
    List<ConsumerRecord<String, String>> completed =
        AnalysisFlowTests.records(AnalysisCompleted.TOPIC, contractId, Duration.ofSeconds(30));
    assertThat(completed).hasSize(1);
    Header traceparent = completed.getFirst().headers().lastHeader("traceparent");
    assertThat(traceparent).as("traceparent on the completed result").isNotNull();
    assertThat(new String(traceparent.value(), StandardCharsets.UTF_8)).contains(traceId);

    // This test's consumer group reads the topic from the start, so other tests' requests are
    // processed too, each in its own trace: look for the spans of this one.
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> span(traceId, SpanKind.CONSUMER, AnalysisRequest.TOPIC), s -> s != null);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> span(traceId, SpanKind.PRODUCER, AnalysisCompleted.TOPIC), s -> s != null);

    // The text did reach the model (other tests' requests are analyzed too, hence anySatisfy)...
    assertThat(model.prompts()).anySatisfy(p -> assertThat(p.toString()).contains(CONTRACT_TEXT));
    // ...and nothing else.
    assertNoContractTextIn(spans.getFinishedSpanItems(), logs.getFinishedLogRecordItems());
  }

  static void assertNoContractTextIn(List<SpanData> spans, List<LogRecordData> logs) {
    assertThat(spans).isNotEmpty();
    for (SpanData span : spans) {
      String all =
          span.getName()
              + span.getAttributes()
              + span.getEvents().stream().map(e -> e.getName() + e.getAttributes()).toList();
      assertThat(all).as("span %s", span.getName()).doesNotContain(CONTRACT_TEXT);
    }
    for (LogRecordData log : logs) {
      String body = log.getBodyValue() == null ? "" : log.getBodyValue().asString();
      assertThat(body + log.getAttributes()).as("log record").doesNotContain(CONTRACT_TEXT);
    }
  }

  private SpanData span(String traceId, SpanKind kind, String topic) {
    return spans.getFinishedSpanItems().stream()
        .filter(s -> s.getTraceId().equals(traceId))
        .filter(s -> s.getKind() == kind && s.getName().contains(topic))
        .findFirst()
        .orElse(null);
  }

  private static void send(UUID key, String json, String traceparent) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      ProducerRecord<String, String> record =
          new ProducerRecord<>(AnalysisRequest.TOPIC, key.toString(), json);
      record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
      producer.send(record);
    }
  }
}
