package ma.mizan.analyzer;

import java.time.Instant;
import java.util.UUID;

/** Consumed from {@value #TOPIC}; published by mizan-api (architecture §5, schema version 1). */
public record AnalysisRequest(
    int schemaVersion,
    UUID analysisId,
    UUID contractId,
    String language,
    String text,
    Instant requestedAt) {

  public static final String TOPIC = "contract.analysis-requested";
  public static final String DLT = TOPIC + ".DLT";
}
