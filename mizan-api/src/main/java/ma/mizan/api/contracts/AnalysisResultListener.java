package ma.mizan.api.contracts;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Story 3.3: analyzer results in. Each topic maps JSON to its own record (no type headers). */
@Component
class AnalysisResultListener {

  private final AnalysisResultService results;

  AnalysisResultListener(AnalysisResultService results) {
    this.results = results;
  }

  @KafkaListener(
      topics = AnalysisResults.COMPLETED_TOPIC,
      properties =
          "spring.json.value.default.type=ma.mizan.api.contracts.AnalysisResults$Completed")
  void onCompleted(AnalysisResults.Completed result) {
    results.complete(result);
  }

  @KafkaListener(
      topics = AnalysisResults.FAILED_TOPIC,
      properties = "spring.json.value.default.type=ma.mizan.api.contracts.AnalysisResults$Failed")
  void onFailed(AnalysisResults.Failed result) {
    results.fail(result);
  }

  @KafkaListener(
      topics = AnalysisResults.STARTED_TOPIC,
      properties = "spring.json.value.default.type=ma.mizan.api.contracts.AnalysisResults$Started")
  void onStarted(AnalysisResults.Started started) {
    results.start(started);
  }
}
