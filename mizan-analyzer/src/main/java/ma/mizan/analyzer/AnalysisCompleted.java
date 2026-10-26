package ma.mizan.analyzer;

import java.util.List;
import java.util.UUID;

/** Published to {@value #TOPIC} when the model returned a valid analysis. */
public record AnalysisCompleted(
    int schemaVersion,
    UUID analysisId,
    UUID contractId,
    String summary,
    List<ClauseFlag> flags,
    String model,
    int inputTokens,
    int outputTokens) {

  public static final int SCHEMA_VERSION = 1;
  public static final String TOPIC = "contract.analysis-completed";
}
