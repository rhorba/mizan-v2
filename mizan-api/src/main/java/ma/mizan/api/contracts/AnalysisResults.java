package ma.mizan.api.contracts;

import java.util.List;
import java.util.UUID;

/** Result messages published by mizan-analyzer (architecture §5, schema version 1). */
final class AnalysisResults {

  static final String COMPLETED_TOPIC = "contract.analysis-completed";
  static final String FAILED_TOPIC = "contract.analysis-failed";

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

  record Failed(int schemaVersion, UUID analysisId, UUID contractId, String reason) {}
}
