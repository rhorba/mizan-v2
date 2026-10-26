package ma.mizan.analyzer;

import java.util.UUID;

/** Published to {@value #TOPIC} when retries are exhausted; the request also goes to the DLT. */
public record AnalysisFailed(int schemaVersion, UUID analysisId, UUID contractId, String reason) {

  public static final int SCHEMA_VERSION = 1;
  public static final String TOPIC = "contract.analysis-failed";
}
