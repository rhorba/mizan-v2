package ma.mizan.analyzer;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@value #TOPIC} when the analyzer picks a request up (Story 3.4: PENDING to
 * ANALYZING). Sent again on each Kafka retry; the API applies it only to a PENDING analysis.
 */
public record AnalysisStarted(
    int schemaVersion, UUID analysisId, UUID contractId, Instant startedAt) {

  public static final int SCHEMA_VERSION = 1;
  public static final String TOPIC = "contract.analysis-started";
}
