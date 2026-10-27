package ma.mizan.api.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result messages published by mizan-analyzer (architecture §5, schema version 1). */
final class AnalysisResults {

  static final String COMPLETED_TOPIC = "contract.analysis-completed";
  static final String FAILED_TOPIC = "contract.analysis-failed";
  static final String STARTED_TOPIC = "contract.analysis-started";

  private AnalysisResults() {}

  record Completed(
      int schemaVersion,
      UUID analysisId,
      UUID contractId,
      String summary,
      List<Flag> flags,
      String model,
      int inputTokens,
      int outputTokens) {}

  record Flag(
      String clauseText, RiskLevel riskLevel, String explanation, String suggestedCorrection) {}

  /**
   * Schema version 2 adds the tokens billed before failing. Boxed so a version 1 message (no token
   * fields, e.g. from an analyzer not yet redeployed) still applies: missing counts as 0.
   */
  record Failed(
      int schemaVersion,
      UUID analysisId,
      UUID contractId,
      String reason,
      Integer inputTokens,
      Integer outputTokens) {

    long billedInput() {
      return inputTokens == null ? 0 : inputTokens;
    }

    long billedOutput() {
      return outputTokens == null ? 0 : outputTokens;
    }
  }

  /** Schema version 1 (Story 3.4): the analyzer picked the request up. */
  record Started(int schemaVersion, UUID analysisId, UUID contractId, Instant startedAt) {}
}
