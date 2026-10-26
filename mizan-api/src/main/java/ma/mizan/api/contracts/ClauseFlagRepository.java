package ma.mizan.api.contracts;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface ClauseFlagRepository extends Repository<ClauseFlag, UUID> {

  ClauseFlag save(ClauseFlag flag);

  List<ClauseFlag> findByAnalysisIdOrderByPosition(UUID analysisId);
}
