package ma.mizan.api.contracts;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/** ADR-2: the analysis topics, and how result messages that cannot be applied are handled. */
@Configuration
@EnableScheduling
class AnalysisMessagingConfig {

  @Bean
  NewTopic analysisRequestedTopic() {
    return TopicBuilder.name(AnalysisRequested.TOPIC).partitions(3).replicas(1).build();
  }

  /** Also declared by the analyzer (idempotent): the API may start first and consume them. */
  @Bean
  NewTopic analysisCompletedTopic() {
    return TopicBuilder.name(AnalysisResults.COMPLETED_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic analysisStartedTopic() {
    return TopicBuilder.name(AnalysisResults.STARTED_TOPIC).partitions(3).replicas(1).build();
  }

  /** Story 3.4: status broadcast between API instances (each consumes it all). */
  @Bean
  NewTopic analysisStatusTopic() {
    return TopicBuilder.name(AnalysisStatusChanged.TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic analysisFailedTopic() {
    return TopicBuilder.name(AnalysisResults.FAILED_TOPIC).partitions(3).replicas(1).build();
  }

  /**
   * Story 3.3: a result that still fails after 3 tries (database down, unreadable JSON) is logged
   * and skipped so it cannot block the partition; the analysis stays non-final and is visible to
   * the stuck-analysis query (docs/database §6).
   */
  @Bean
  DefaultErrorHandler analysisResultErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
  }
}
