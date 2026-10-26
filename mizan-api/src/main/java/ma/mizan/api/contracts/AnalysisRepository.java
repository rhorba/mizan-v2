package ma.mizan.api.contracts;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AnalysisRepository extends Repository<Analysis, UUID> {

  Analysis save(Analysis analysis);

  Optional<Analysis> findById(UUID id);

  List<Analysis> findByIdIn(Collection<UUID> ids);

  List<Analysis> findByContractIdOrderByRequestedAtDesc(UUID contractId);

  /** All analyses of one history page in a single query (idx_analyses_contract). */
  List<Analysis> findByContractIdInOrderByRequestedAtDesc(Collection<UUID> contractIds);

  /**
   * Story 3.3 / ADR-4: one conditional UPDATE is the idempotency guard. Returns 0 when the analysis
   * is unknown, belongs to another contract, was deleted or is already terminal.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Analysis a
         set a.status = ma.mizan.api.contracts.AnalysisStatus.COMPLETE,
             a.summary = :summary, a.model = :model,
             a.inputTokens = :inputTokens, a.outputTokens = :outputTokens,
             a.completedAt = :completedAt, a.version = a.version + 1
       where a.id = :id and a.contract.id = :contractId
         and a.status in (ma.mizan.api.contracts.AnalysisStatus.PENDING,
                          ma.mizan.api.contracts.AnalysisStatus.ANALYZING)
      """)
  int complete(
      @Param("id") UUID id,
      @Param("contractId") UUID contractId,
      @Param("summary") String summary,
      @Param("model") String model,
      @Param("inputTokens") int inputTokens,
      @Param("outputTokens") int outputTokens,
      @Param("completedAt") Instant completedAt);

  /** Same guard as {@link #complete}: a late failure never overwrites a result. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Analysis a
         set a.status = ma.mizan.api.contracts.AnalysisStatus.FAILED,
             a.failureReason = :reason, a.completedAt = :completedAt,
             a.version = a.version + 1
       where a.id = :id and a.contract.id = :contractId
         and a.status in (ma.mizan.api.contracts.AnalysisStatus.PENDING,
                          ma.mizan.api.contracts.AnalysisStatus.ANALYZING)
      """)
  int fail(
      @Param("id") UUID id,
      @Param("contractId") UUID contractId,
      @Param("reason") String reason,
      @Param("completedAt") Instant completedAt);

  /** Story 3.4: only a PENDING analysis moves forward; a late or repeated "started" is a no-op. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Analysis a
         set a.status = ma.mizan.api.contracts.AnalysisStatus.ANALYZING,
             a.version = a.version + 1
       where a.id = :id and a.contract.id = :contractId
         and a.status = ma.mizan.api.contracts.AnalysisStatus.PENDING
      """)
  int markAnalyzing(@Param("id") UUID id, @Param("contractId") UUID contractId);
}
