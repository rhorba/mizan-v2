package ma.mizan.api.contracts;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.IncompleteEventPublications;

/** ADR-2: the live sweep resends only publications older than one minute (not in flight). */
class StalePublicationSweepTests {

  @Test
  void resendsPublicationsOlderThanOneMinute() {
    IncompleteEventPublications incomplete = mock(IncompleteEventPublications.class);

    new StalePublicationSweep(incomplete).resend();

    verify(incomplete).resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
  }
}
