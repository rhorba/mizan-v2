package ma.mizan.api.shared.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Story 6.1: Modulith externalizes the outbox to Kafka from an {@code @ApplicationModuleListener},
 * which is {@code @Async}. Without this decorator on Boot's task executor the send starts a new
 * trace and the analyzer's work is cut off from the upload that caused it.
 */
@Configuration(proxyBeanMethods = false)
class AsyncTracing {

  @Bean
  TaskDecorator contextPropagatingTaskDecorator() {
    return new ContextPropagatingTaskDecorator();
  }
}
