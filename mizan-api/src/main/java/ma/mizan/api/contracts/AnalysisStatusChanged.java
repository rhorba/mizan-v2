package ma.mizan.api.contracts;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Story 3.4 / ADR-7: a status change, published in the transaction that made it and broadcast on
 * {@value #TOPIC} so that every API instance can push it to the browsers it holds streams for.
 * Carries no owner and no text: the owner is checked when a stream is opened.
 */
@Externalized("contract.analysis-status::#{contractId()}")
public record AnalysisStatusChanged(
    int schemaVersion, UUID analysisId, UUID contractId, AnalysisStatus status, Instant changedAt) {

  public static final int SCHEMA_VERSION = 1;
  public static final String TOPIC = "contract.analysis-status";

  static AnalysisStatusChanged of(UUID analysisId, UUID contractId, AnalysisStatus status) {
    return new AnalysisStatusChanged(SCHEMA_VERSION, analysisId, contractId, status, Instant.now());
  }
}
