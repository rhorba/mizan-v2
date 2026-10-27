package ma.mizan.analyzer;

import java.util.UUID;

/**
 * Published to {@value #TOPIC} when retries are exhausted; the request also goes to the DLT. Schema
 * version 2 adds the tokens billed before failing (spend budget, Story 5.1).
 */
public record AnalysisFailed(
    int schemaVersion,
    UUID analysisId,
    UUID contractId,
    String reason,
    int inputTokens,
    int outputTokens) {

  public static final int SCHEMA_VERSION = 2;
  public static final String TOPIC = "contract.analysis-failed";
}
