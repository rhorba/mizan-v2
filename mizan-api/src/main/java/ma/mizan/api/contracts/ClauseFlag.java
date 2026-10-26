package ma.mizan.api.contracts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One risky clause of a COMPLETE analysis (Story 3.3), in the model's order. */
@Entity
@Table(name = "clause_flags")
class ClauseFlag {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "analysis_id", nullable = false, updatable = false)
  private UUID analysisId;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "clause_text", nullable = false, columnDefinition = "text")
  private String clauseText;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_level", nullable = false, length = 6)
  private RiskLevel riskLevel;

  @Column(name = "explanation", nullable = false, columnDefinition = "text")
  private String explanation;

  @Column(name = "suggested_correction", columnDefinition = "text")
  private String suggestedCorrection;

  protected ClauseFlag() {}

  ClauseFlag(
      UUID analysisId,
      int position,
      String clauseText,
      RiskLevel riskLevel,
      String explanation,
      String suggestedCorrection) {
    this.analysisId = analysisId;
    this.position = position;
    this.clauseText = clauseText;
    this.riskLevel = riskLevel;
    this.explanation = explanation;
    this.suggestedCorrection = suggestedCorrection;
  }

  UUID analysisId() {
    return analysisId;
  }

  int position() {
    return position;
  }

  String clauseText() {
    return clauseText;
  }

  RiskLevel riskLevel() {
    return riskLevel;
  }

  String explanation() {
    return explanation;
  }

  String suggestedCorrection() {
    return suggestedCorrection;
  }
}
