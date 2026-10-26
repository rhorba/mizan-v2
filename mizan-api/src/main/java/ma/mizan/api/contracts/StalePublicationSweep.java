package ma.mizan.api.contracts;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ADR-2: resends event publications Kafka never acknowledged while the API keeps running. Restarts
 * are covered by {@code republish-outstanding-events-on-restart}.
 */
@Component
@ConditionalOnProperty(name = "mizan.events.resend-stale", matchIfMissing = true)
class StalePublicationSweep {

  private static final Logger LOG = LoggerFactory.getLogger(StalePublicationSweep.class);

  /** Older than this and still incomplete means the first send failed, not that it is in flight. */
  static final Duration RESEND_AFTER = Duration.ofMinutes(1);

  private final IncompleteEventPublications incomplete;

  StalePublicationSweep(IncompleteEventPublications incomplete) {
    this.incomplete = incomplete;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  void resend() {
    LOG.debug("Resending event publications older than {}", RESEND_AFTER);
    incomplete.resubmitIncompletePublicationsOlderThan(RESEND_AFTER);
  }
}
