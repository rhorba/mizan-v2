package ma.mizan.api.admin;

import java.time.Instant;
import java.util.UUID;
import ma.mizan.api.contracts.AnalysisStatus;

/**
 * One analysis request in the dead-letter topic, as the admin screen shows it. Never carries the
 * contract text. {@code status} is the analysis status now, null once its contract is gone.
 */
record DeadLetter(
    int partition,
    long offset,
    Instant failedAt,
    UUID analysisId,
    UUID contractId,
    String language,
    String error,
    AnalysisStatus status) {

  DeadLetter withStatus(AnalysisStatus current) {
    return new DeadLetter(
        partition, offset, failedAt, analysisId, contractId, language, error, current);
  }
}
