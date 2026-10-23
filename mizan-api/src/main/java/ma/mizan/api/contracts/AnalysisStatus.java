package ma.mizan.api.contracts;

/** FR-5: PENDING → ANALYZING → COMPLETE | FAILED. */
public enum AnalysisStatus {
  PENDING,
  ANALYZING,
  COMPLETE,
  FAILED
}
