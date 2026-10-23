package ma.mizan.api.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every read takes the owner: there is no "find by id" without it (docs/security §4). */
interface ContractRepository extends Repository<Contract, UUID> {

  Contract save(Contract contract);

  Optional<Contract> findByIdAndOwnerSub(UUID id, String ownerSub);

  /** First history page, newest first (idx_contracts_owner_created). */
  @Query(
      value =
          "SELECT * FROM {h-schema}contracts WHERE owner_sub = :owner"
              + " ORDER BY created_at DESC, id DESC LIMIT :limit",
      nativeQuery = true)
  List<Contract> findFirstPage(@Param("owner") String ownerSub, @Param("limit") int limit);

  /** Next history page after the keyset (created_at, id) of the last row seen. */
  @Query(
      value =
          "SELECT * FROM {h-schema}contracts WHERE owner_sub = :owner"
              + " AND (created_at, id) < (:createdAt, :id)"
              + " ORDER BY created_at DESC, id DESC LIMIT :limit",
      nativeQuery = true)
  List<Contract> findPageAfter(
      @Param("owner") String ownerSub,
      @Param("createdAt") Instant createdAt,
      @Param("id") UUID id,
      @Param("limit") int limit);

  /** Analyses and flags go with it (ON DELETE CASCADE). Returns 0 for foreign or missing ids. */
  @Modifying
  @Query("DELETE FROM Contract c WHERE c.id = :id AND c.ownerSub = :owner")
  int deleteByIdAndOwnerSub(@Param("id") UUID id, @Param("owner") String ownerSub);
}
