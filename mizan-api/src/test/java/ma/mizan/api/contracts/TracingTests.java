package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import ma.mizan.api.shared.kafka.KafkaTestcontainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Story 6.1 in the API: one trace from the upload request through the outbox to the Kafka message
 * (traceparent header) the analyzer continues, and from the analyzer's result back into the apply.
 * Spans and logs never carry the contract text or the file name.
 */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = {
      "management.opentelemetry.tracing.export.schedule-delay=50ms",
      "management.opentelemetry.logging.export.schedule-delay=50ms"
    })
@AutoConfigureMockMvc
@Import(TracingTests.Exporters.class)
class TracingTests {

  /** Text that is in the PDF and must never reach a span or a log record. */
  static final String CONTRACT_TEXT = "Le prestataire s'engage a livrer le travail";

  static final String FILE_NAME = "bail-confidentiel.pdf";

  @TestConfiguration(proxyBeanMethods = false)
  static class Exporters {
    @Bean
    InMemorySpanExporter spans() {
      return InMemorySpanExporter.create();
    }

    @Bean
    InMemoryLogRecordExporter logs() {
      return InMemoryLogRecordExporter.create();
    }
  }

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired InMemorySpanExporter spans;
  @Autowired InMemoryLogRecordExporter logs;

  @BeforeEach
  void clean() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    jdbc.sql("DELETE FROM mizan.daily_usage").update();
    spans.reset();
    logs.reset();
  }

  @Test
  void uploadRequestOutboxAndKafkaMessageShareOneTraceWithoutContractText() {
    MvcTestResult upload =
        mvc.post()
            .uri("/api/v1/contracts")
            .multipart()
            .file(
                new MockMultipartFile("file", FILE_NAME, "application/pdf", PdfFixtures.textPdf(1)))
            .param("language", "fr")
            .with(user("tracer"))
            .exchange();
    assertThat(upload).hasStatus(HttpStatus.ACCEPTED);
    String contractId =
        upload.getResponse().getHeader("Location").replace("/api/v1/contracts/", "");

    SpanData request =
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> span(SpanKind.SERVER, "/api/v1/contracts"), s -> s != null);
    String traceId = request.getTraceId();

    // The outbox sends after commit, on an async thread, still inside the request's trace.
    await()
        .atMost(Duration.ofSeconds(20))
        .alias("outbox send joins the upload trace")
        .until(
            () ->
                spans.getFinishedSpanItems().stream()
                    .anyMatch(
                        s ->
                            s.getTraceId().equals(traceId)
                                && s.getKind() == SpanKind.PRODUCER
                                && s.getName().contains(AnalysisRequested.TOPIC)));

    // The analyzer continues from the W3C header on the message.
    ConsumerRecord<String, String> message = requestFor(contractId);
    Header traceparent = message.headers().lastHeader("traceparent");
    assertThat(traceparent).as("traceparent header on the analysis request").isNotNull();
    assertThat(new String(traceparent.value(), StandardCharsets.UTF_8)).contains(traceId);

    // The audit line is a log record of the same trace.
    LogRecordData audit =
        await()
            .atMost(Duration.ofSeconds(10))
            .until(
                () ->
                    logs.getFinishedLogRecordItems().stream()
                        .filter(l -> body(l).contains("action=contract.upload "))
                        .findFirst()
                        .orElse(null),
                l -> l != null);
    assertThat(audit.getSpanContext().getTraceId()).isEqualTo(traceId);

    assertNoSecretsIn(spans.getFinishedSpanItems(), logs.getFinishedLogRecordItems());
  }

  @Test
  void applyingAResultContinuesTheAnalyzersTrace() throws Exception {
    UUID contractId = UUID.randomUUID();
    UUID analysisId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'tracer', 'x.pdf', 1, ?)")
        .params(contractId, CONTRACT_TEXT)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                + " VALUES (?, ?, 'fr', 'ANALYZING')")
        .params(analysisId, contractId)
        .update();
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    String completed =
        """
        {"schemaVersion":1,"analysisId":"%s","contractId":"%s","summary":"Résumé.",
         "flags":[],"model":"claude-opus-5-5","inputTokens":10,"outputTokens":10}
        """
            .formatted(analysisId, contractId);

    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      ProducerRecord<String, String> record =
          new ProducerRecord<>(AnalysisResults.COMPLETED_TOPIC, contractId.toString(), completed);
      record
          .headers()
          .add(
              "traceparent",
              ("00-" + traceId + "-00f067aa0ba902b7-01").getBytes(StandardCharsets.UTF_8));
      producer.send(record).get();
    }

    SpanData apply =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> span(SpanKind.CONSUMER, AnalysisResults.COMPLETED_TOPIC), s -> s != null);
    assertThat(apply.getTraceId()).isEqualTo(traceId);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.sql("SELECT status FROM mizan.analyses WHERE id = ?")
                            .param(analysisId)
                            .query(String.class)
                            .single())
                    .isEqualTo("COMPLETE"));
  }

  /** Every attribute and event of every span, and every log body and attribute, is checked. */
  static void assertNoSecretsIn(List<SpanData> spans, List<LogRecordData> logs) {
    assertThat(spans).isNotEmpty();
    for (SpanData span : spans) {
      String all =
          span.getName()
              + span.getAttributes()
              + span.getEvents().stream().map(e -> e.getName() + e.getAttributes()).toList();
      assertThat(all)
          .as("span %s", span.getName())
          .doesNotContain(CONTRACT_TEXT, FILE_NAME, "Bearer ");
    }
    for (LogRecordData log : logs) {
      assertThat(body(log) + log.getAttributes())
          .as("log record")
          .doesNotContain(CONTRACT_TEXT, FILE_NAME, "Bearer ");
    }
  }

  private SpanData span(SpanKind kind, String nameFragment) {
    return spans.getFinishedSpanItems().stream()
        .filter(s -> s.getKind() == kind && s.getName().contains(nameFragment))
        .findFirst()
        .orElse(null);
  }

  private static String body(LogRecordData log) {
    return log.getBodyValue() == null ? "" : log.getBodyValue().asString();
  }

  private static ConsumerRecord<String, String> requestFor(String contractId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestcontainer.container().getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "tracing-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    try (var consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(AnalysisRequested.TOPIC));
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
          if (contractId.equals(r.key())) {
            return r;
          }
        }
      }
    }
    throw new AssertionError("no analysis request for contract " + contractId);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor user(
      String subject) {
    return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user"));
  }
}
