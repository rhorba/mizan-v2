package ma.mizan.api.contracts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One analysis of a contract in one language. Result fields are filled by Story 3.3. */
@Entity
@Table(name = "analyses")
class Analysis {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contract_id", nullable = false, updatable = false)
  private Contract contract;

  @Column(name = "language", nullable = false, length = 3)
  private Language language;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 12)
  private AnalysisStatus status;

  @Column(name = "summary", columnDefinition = "text")
  private String summary;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "model", length = 64)
  private String model;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Column(name = "output_tokens")
  private Integer outputTokens;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_output", columnDefinition = "jsonb")
  private String rawOutput;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Analysis() {}

  Analysis(Contract contract, Language language) {
    this.contract = contract;
    this.language = language;
    this.status = AnalysisStatus.PENDING;
    this.requestedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  UUID id() {
    return id;
  }

  Contract contract() {
    return contract;
  }

  Language language() {
    return language;
  }

  AnalysisStatus status() {
    return status;
  }

  Instant requestedAt() {
    return requestedAt;
  }

  String summary() {
    return summary;
  }

  String failureReason() {
    return failureReason;
  }

  Instant completedAt() {
    return completedAt;
  }

  /** Admin retry of a dead-lettered analysis: back to PENDING with every result field cleared. */
  void retry() {
    if (status != AnalysisStatus.FAILED) {
      throw new IllegalStateException("only a FAILED analysis can be retried");
    }
    status = AnalysisStatus.PENDING;
    failureReason = null;
    summary = null;
    model = null;
    inputTokens = null;
    outputTokens = null;
    completedAt = null;
  }
}
