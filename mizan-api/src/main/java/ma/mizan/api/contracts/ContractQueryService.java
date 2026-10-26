package ma.mizan.api.contracts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-6: the owner's history, one contract, and deletion. All scoped by the owner. */
@Service
class ContractQueryService {

  static final int PAGE_SIZE = 20;
  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  private final ContractRepository contracts;
  private final AnalysisRepository analyses;
  private final ClauseFlagRepository flags;

  ContractQueryService(
      ContractRepository contracts, AnalysisRepository analyses, ClauseFlagRepository flags) {
    this.contracts = contracts;
    this.analyses = analyses;
    this.flags = flags;
  }

  /** A contract with its most recent analysis (null only if none exists). */
  record Entry(Contract contract, Analysis latest) {}

  record Page(List<Entry> entries, HistoryCursor next) {}

  @Transactional(readOnly = true)
  Page history(String ownerSub, String cursor) {
    // One extra row tells whether another page exists.
    List<Contract> rows =
        cursor == null
            ? contracts.findFirstPage(ownerSub, PAGE_SIZE + 1)
            : afterCursor(ownerSub, HistoryCursor.decode(cursor));
    List<Contract> page = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
    Map<UUID, Analysis> latest = new LinkedHashMap<>();
    if (!page.isEmpty()) {
      analyses
          .findByContractIdInOrderByRequestedAtDesc(page.stream().map(Contract::id).toList())
          .forEach(a -> latest.putIfAbsent(a.contract().id(), a));
    }
    List<Entry> entries = page.stream().map(c -> new Entry(c, latest.get(c.id()))).toList();
    HistoryCursor next = rows.size() > PAGE_SIZE ? HistoryCursor.after(page.getLast()) : null;
    return new Page(entries, next);
  }

  @Transactional(readOnly = true)
  Entry get(String ownerSub, UUID id) {
    Contract contract =
        contracts
            .findByIdAndOwnerSub(id, ownerSub)
            .orElseThrow(() -> new ContractProblemException(ContractProblem.NOT_FOUND));
    List<Analysis> all = analyses.findByContractIdOrderByRequestedAtDesc(contract.id());
    return new Entry(contract, all.isEmpty() ? null : all.getFirst());
  }

  /** Story 4.1: one analysis of an owned contract, with its flags in the model's order. */
  @Transactional(readOnly = true)
  Result result(String ownerSub, UUID contractId, UUID analysisId) {
    Contract contract =
        contracts
            .findByIdAndOwnerSub(contractId, ownerSub)
            .orElseThrow(() -> new ContractProblemException(ContractProblem.NOT_FOUND));
    Analysis analysis =
        analyses
            .findByIdAndContractId(analysisId, contract.id())
            .orElseThrow(() -> new ContractProblemException(ContractProblem.ANALYSIS_NOT_FOUND));
    return new Result(analysis, flags.findByAnalysisIdOrderByPosition(analysis.id()));
  }

  record Result(Analysis analysis, List<ClauseFlag> flags) {}

  @Transactional
  void delete(String ownerSub, UUID id) {
    if (contracts.deleteByIdAndOwnerSub(id, ownerSub) == 0) {
      throw new ContractProblemException(ContractProblem.NOT_FOUND);
    }
    AUDIT.info("action=contract.delete subject={} contract={}", ownerSub, id);
  }

  private List<Contract> afterCursor(String ownerSub, HistoryCursor cursor) {
    return contracts.findPageAfter(ownerSub, cursor.createdAt(), cursor.id(), PAGE_SIZE + 1);
  }
}
