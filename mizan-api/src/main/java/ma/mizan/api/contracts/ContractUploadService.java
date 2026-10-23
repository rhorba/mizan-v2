package ma.mizan.api.contracts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-3: validate, extract, persist the contract and a PENDING analysis in one transaction. */
@Service
class ContractUploadService {

  /** Audit trail (docs/security §2, Repudiation): ids only, never file names or text. */
  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  private final MalwareScanner scanner;
  private final PdfInspector inspector;
  private final ContractRepository contracts;
  private final AnalysisRepository analyses;

  ContractUploadService(
      MalwareScanner scanner,
      PdfInspector inspector,
      ContractRepository contracts,
      AnalysisRepository analyses) {
    this.scanner = scanner;
    this.inspector = inspector;
    this.contracts = contracts;
    this.analyses = analyses;
  }

  @Transactional
  Analysis upload(String ownerSub, String fileName, byte[] bytes, Language language) {
    // Scan before any parser touches the bytes.
    scanner.check(ownerSub, bytes);
    PdfInspector.Inspection pdf = inspector.inspect(bytes);
    Contract contract =
        contracts.save(
            new Contract(ownerSub, FileNames.sanitize(fileName), pdf.pageCount(), pdf.text()));
    Analysis analysis = analyses.save(new Analysis(contract, language));
    AUDIT.info(
        "action=contract.upload subject={} contract={} analysis={} pages={} language={}",
        ownerSub,
        contract.id(),
        analysis.id(),
        pdf.pageCount(),
        language.code());
    return analysis;
  }
}
