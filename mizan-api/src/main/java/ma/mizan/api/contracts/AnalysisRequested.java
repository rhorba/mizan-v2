package ma.mizan.api.contracts;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Story 3.1, ADR-2: published inside the upload transaction, stored by the event publication
 * registry and sent to Kafka after commit, keyed by contract id. Wire format: architecture §5.
 */
@Externalized("contract.analysis-requested::#{contractId()}")
public record AnalysisRequested(
    int schemaVersion,
    UUID analysisId,
    UUID contractId,
    String language,
    String text,
    Instant requestedAt) {

  public static final int SCHEMA_VERSION = 1;
  public static final String TOPIC = "contract.analysis-requested";

  static AnalysisRequested of(Analysis analysis) {
    Contract contract = analysis.contract();
    return new AnalysisRequested(
        SCHEMA_VERSION,
        analysis.id(),
        contract.id(),
        analysis.language().code(),
        contract.textContent(),
        analysis.requestedAt());
  }
}
