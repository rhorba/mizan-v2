package ma.mizan.api.contracts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ma.mizan.api.quota.DailyUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 3.3 / ADR-4: applies analyzer results at most once. Duplicates, late messages and results
 * for unknown or deleted contracts are logged and dropped.
 */
@Service
class AnalysisResultService {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisResultService.class);

  /** Audit trail (docs/security §2): ids only, never contract text or model output. */
  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  static final int MODEL_MAX = 64;
  static final int REASON_MAX = 255;

  private final AnalysisRepository analyses;
  private final ClauseFlagRepository flags;
  private final DailyUsage usage;

  AnalysisResultService(AnalysisRepository analyses, ClauseFlagRepository flags, DailyUsage usage) {
    this.analyses = analyses;
    this.flags = flags;
    this.usage = usage;
  }

  /** Returns false when the message was dropped. */
  @Transactional
  boolean complete(AnalysisResults.Completed result) {
    int updated =
        analyses.complete(
            result.analysisId(),
            result.contractId(),
            result.summary(),
            truncate(result.model(), MODEL_MAX),
            result.inputTokens(),
            result.outputTokens(),
            now());
    if (updated == 0) {
      dropped("completed", result.analysisId());
      return false;
    }
    Analysis applied = analyses.findById(result.analysisId()).orElseThrow();
    usage.recordTokens(
        applied.contract().ownerSub(),
        applied.requestedAt(),
        result.inputTokens(),
        result.outputTokens());
    List<AnalysisResults.Flag> received = result.flags() == null ? List.of() : result.flags();
    for (int position = 0; position < received.size(); position++) {
      AnalysisResults.Flag flag = received.get(position);
      flags.save(
          new ClauseFlag(
              result.analysisId(),
              position,
              flag.clauseText(),
              flag.riskLevel(),
              flag.explanation(),
              blankToNull(flag.suggestedCorrection())));
    }
    AUDIT.info(
        "action=analysis.complete analysis={} contract={} flags={} model={} inputTokens={}"
            + " outputTokens={}",
        result.analysisId(),
        result.contractId(),
        received.size(),
        result.model(),
        result.inputTokens(),
        result.outputTokens());
    return true;
  }

  /** Returns false when the message was dropped. */
  @Transactional
  boolean fail(AnalysisResults.Failed result) {
    int updated =
        analyses.fail(
            result.analysisId(), result.contractId(), truncate(result.reason(), REASON_MAX), now());
    if (updated == 0) {
      dropped("failed", result.analysisId());
      return false;
    }
    // Story 5.1: a FAILED analysis refunds its unit; the tokens it burned still count.
    Analysis applied = analyses.findById(result.analysisId()).orElseThrow();
    String owner = applied.contract().ownerSub();
    usage.refund(owner, applied.requestedAt());
    usage.recordTokens(owner, applied.requestedAt(), result.billedInput(), result.billedOutput());
    AUDIT.info(
        "action=analysis.fail analysis={} contract={} reason={}",
        result.analysisId(),
        result.contractId(),
        result.reason());
    return true;
  }

  private static void dropped(String kind, Object analysisId) {
    LOG.info(
        "Dropped {} result for analysis={}: unknown, deleted or already final", kind, analysisId);
  }

  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
