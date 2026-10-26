package ma.mizan.analyzer;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Story 3.2: request in, result out. Failures are left to the error handler ({@link
 * AnalyzerKafkaConfig}): retries with back-off, then analysis-failed and the DLT.
 */
@Component
class AnalysisListener {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisListener.class);
  static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

  private final ContractAnalyzer analyzer;
  private final KafkaTemplate<Object, Object> kafka;
  private final MeterRegistry meters;

  AnalysisListener(
      ContractAnalyzer analyzer, KafkaTemplate<Object, Object> kafka, MeterRegistry meters) {
    this.analyzer = analyzer;
    this.kafka = kafka;
    this.meters = meters;
  }

  @KafkaListener(topics = AnalysisRequest.TOPIC)
  void onRequest(AnalysisRequest request) throws Exception {
    ContractAnalyzer.Result result = analyzer.analyze(request);
    AnalysisCompleted completed =
        new AnalysisCompleted(
            AnalysisCompleted.SCHEMA_VERSION,
            request.analysisId(),
            request.contractId(),
            result.output().summary(),
            result.output().flags(),
            result.model(),
            result.inputTokens(),
            result.outputTokens());
    // Wait for the broker: the request offset must not be committed before the result is stored.
    kafka
        .send(AnalysisCompleted.TOPIC, request.contractId().toString(), completed)
        .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    meters.counter("mizan.analysis.outcomes", "outcome", "completed").increment();
    meters.counter("mizan.analysis.tokens", "type", "input").increment(result.inputTokens());
    meters.counter("mizan.analysis.tokens", "type", "output").increment(result.outputTokens());
    LOG.info(
        "action=analysis.completed analysis={} model={} flags={} inputTokens={} outputTokens={}",
        request.analysisId(),
        result.model(),
        result.output().flags().size(),
        result.inputTokens(),
        result.outputTokens());
  }
}
