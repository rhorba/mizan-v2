package ma.mizan.api.contracts;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module API for the admin dead-letter screen (Sprint 3 🔴): current statuses, and a retry that
 * resets a FAILED analysis to PENDING and requests it again through the outbox (ADR-2). The request
 * is rebuilt from the stored contract text, so a deleted contract cannot be retried.
 */
@Service
public class AnalysisRetry {

  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  /** What a retry did. */
  public enum Outcome {
    REQUEUED,
    NOT_FOUND,
    NOT_FAILED
  }

  private final AnalysisRepository analyses;
  private final ApplicationEventPublisher events;

  AnalysisRetry(AnalysisRepository analyses, ApplicationEventPublisher events) {
    this.analyses = analyses;
    this.events = events;
  }

  /** Status of each analysis that still exists; deleted ones are absent from the map. */
  @Transactional(readOnly = true)
  public Map<UUID, AnalysisStatus> statuses(Collection<UUID> analysisIds) {
    if (analysisIds.isEmpty()) {
      return Map.of();
    }
    return analyses.findByIdIn(analysisIds).stream()
        .collect(Collectors.toMap(Analysis::id, Analysis::status));
  }

  @Transactional
  public Outcome retry(UUID analysisId, String adminSub) {
    Analysis analysis = analyses.findById(analysisId).orElse(null);
    if (analysis == null) {
      return Outcome.NOT_FOUND;
    }
    if (analysis.status() != AnalysisStatus.FAILED) {
      return Outcome.NOT_FAILED;
    }
    analysis.retry();
    events.publishEvent(AnalysisRequested.of(analysis));
    events.publishEvent(
        AnalysisStatusChanged.of(analysis.id(), analysis.contract().id(), AnalysisStatus.PENDING));
    AUDIT.info(
        "action=analysis.retry subject={} analysis={} contract={}",
        adminSub,
        analysis.id(),
        analysis.contract().id());
    return Outcome.REQUEUED;
  }
}
