package ma.mizan.api.contracts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface AnalysisRepository extends Repository<Analysis, UUID> {

  Analysis save(Analysis analysis);

  List<Analysis> findByContractIdOrderByRequestedAtDesc(UUID contractId);

  /** All analyses of one history page in a single query (idx_analyses_contract). */
  List<Analysis> findByContractIdInOrderByRequestedAtDesc(Collection<UUID> contractIds);
}
