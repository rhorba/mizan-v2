package ma.mizan.analyzer;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * ADR-4 / Story 3.2: transient failures (timeouts, 5xx, 429) are retried {@value #RETRIES} times
 * with exponential back-off; unusable answers are not retried by Kafka. Either way the analysis is
 * reported as failed and the request lands in {@link AnalysisRequest#DLT}.
 */
@Configuration
class AnalyzerKafkaConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyzerKafkaConfig.class);
  static final int RETRIES = 3;

  /**
   * Also declared by mizan-api (idempotent). Declaring it here too means the analyzer never
   * auto-creates a 1-partition topic when it starts first and then misses the other partitions.
   */
  @Bean
  NewTopic analysisRequestedTopic() {
    return TopicBuilder.name(AnalysisRequest.TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic analysisCompletedTopic() {
    return TopicBuilder.name(AnalysisCompleted.TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic analysisFailedTopic() {
    return TopicBuilder.name(AnalysisFailed.TOPIC).partitions(3).replicas(1).build();
  }

  /** Same partition count as the request topic: the DLT recoverer keeps the partition. */
  @Bean
  NewTopic analysisRequestedDltTopic() {
    return TopicBuilder.name(AnalysisRequest.DLT).partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler analysisErrorHandler(
      KafkaTemplate<Object, Object> kafka,
      DefaultKafkaProducerFactory<Object, Object> producerFactory,
      MeterRegistry meters,
      @Value("${mizan.analyzer.retry.initial-interval:PT2S}") Duration initialInterval,
      @Value("${mizan.analyzer.retry.multiplier:3}") double multiplier) {
    // Records the deserializer rejected carry their raw bytes: publish those as bytes, not JSON.
    Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
    templates.put(
        byte[].class,
        new KafkaTemplate<>(
            producerFactory.copyWithConfigurationOverride(
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))));
    templates.put(Object.class, kafka);
    // Explicit name: spring-kafka 4 defaults to "<topic>-dlt"; architecture §5 says ".DLT".
    DeadLetterPublishingRecoverer deadLetters =
        new DeadLetterPublishingRecoverer(
            templates,
            (record, failure) -> new TopicPartition(AnalysisRequest.DLT, record.partition()));

    ConsumerRecordRecoverer recoverer =
        (record, failure) -> {
          reportFailed(kafka, record, failure);
          meters.counter("mizan.analysis.outcomes", "outcome", "failed").increment();
          deadLetters.accept(record, failure);
        };
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(RETRIES);
    backOff.setInitialInterval(initialInterval.toMillis());
    backOff.setMultiplier(multiplier);
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(InvalidOutputException.class);
    return handler;
  }

  private static void reportFailed(
      KafkaTemplate<Object, Object> kafka, ConsumerRecord<?, ?> record, Exception failure) {
    if (!(record.value() instanceof AnalysisRequest request)) {
      // Unreadable message: no ids to report; it still goes to the DLT.
      LOG.error(
          "Unreadable analysis request at offset {}: {}", record.offset(), failure.toString());
      return;
    }
    String reason = reason(failure);
    // Tokens already spent on unusable answers still count (spend budget); outages bill nothing.
    InvalidOutputException billed =
        rootCause(failure) instanceof InvalidOutputException invalid ? invalid : null;
    LOG.error(
        "action=analysis.failed analysis={} reason={} cause={}",
        request.analysisId(),
        reason,
        rootCause(failure).toString());
    try {
      kafka
          .send(
              AnalysisFailed.TOPIC,
              request.contractId().toString(),
              new AnalysisFailed(
                  AnalysisFailed.SCHEMA_VERSION,
                  request.analysisId(),
                  request.contractId(),
                  reason,
                  billed == null ? 0 : billed.inputTokens(),
                  billed == null ? 0 : billed.outputTokens()))
          .get(AnalysisListener.SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      // Throwing makes the error handler try the record again, so the failure is not lost.
      throw new IllegalStateException("Could not publish analysis-failed", e);
    }
  }

  /** Stable, user-safe codes; details stay in the logs. */
  static String reason(Exception failure) {
    return rootCause(failure) instanceof InvalidOutputException
        ? "invalid-output"
        : "model-unavailable";
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null && cause.getCause() != cause) {
      if (cause instanceof InvalidOutputException) {
        return cause;
      }
      cause = cause.getCause();
    }
    return cause;
  }
}
