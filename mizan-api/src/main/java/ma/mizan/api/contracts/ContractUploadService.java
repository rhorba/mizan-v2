package ma.mizan.api.contracts;

import ma.mizan.api.quota.DailyUsage;
import ma.mizan.api.quota.QuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-3: validate, extract, persist the contract and a PENDING analysis in one transaction, and
 * request the analysis in that same transaction (ADR-2 outbox).
 */
@Service
class ContractUploadService {

  /** Audit trail (docs/security §2, Repudiation): ids only, never file names or text. */
  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  private final MalwareScanner scanner;
  private final PdfInspector inspector;
  private final ContractRepository contracts;
  private final AnalysisRepository analyses;
  private final ApplicationEventPublisher events;
  private final DailyUsage quota;

  ContractUploadService(
      DailyUsage quota,
      MalwareScanner scanner,
      PdfInspector inspector,
      ContractRepository contracts,
      AnalysisRepository analyses,
      ApplicationEventPublisher events) {
    this.scanner = scanner;
    this.inspector = inspector;
    this.contracts = contracts;
    this.analyses = analyses;
    this.events = events;
    this.quota = quota;
  }

  @Transactional
  Analysis upload(String ownerSub, String fileName, byte[] bytes, Language language) {
    // Cheapest refusal first: no scan or parse once a cap or the spend budget is reached.
    withQuota(() -> quota.check(ownerSub));
    // Scan before any parser touches the bytes.
    scanner.check(ownerSub, bytes);
    PdfInspector.Inspection pdf = inspector.inspect(bytes);
    // Atomic unit (Story 5.1), in this transaction: any later failure gives it back.
    withQuota(() -> quota.reserve(ownerSub));
    Contract contract =
        contracts.save(
            new Contract(ownerSub, FileNames.sanitize(fileName), pdf.pageCount(), pdf.text()));
    Analysis analysis = analyses.save(new Analysis(contract, language));
    events.publishEvent(AnalysisRequested.of(analysis));
    AUDIT.info(
        "action=contract.upload subject={} contract={} analysis={} pages={} language={}",
        ownerSub,
        contract.id(),
        analysis.id(),
        pdf.pageCount(),
        language.code());
    return analysis;
  }

  private static void withQuota(Runnable call) {
    try {
      call.run();
    } catch (QuotaExceededException e) {
      throw new UploadRejectedException(
          switch (e.reason()) {
            case USER_CAP -> UploadProblem.QUOTA_EXCEEDED;
            case GLOBAL_CAP -> UploadProblem.GLOBAL_QUOTA_EXCEEDED;
            case BUDGET -> UploadProblem.BUDGET_EXCEEDED;
          });
    }
  }
}
