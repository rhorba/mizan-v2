package ma.mizan.api.quota;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({QuotaProperties.class, BudgetProperties.class})
class QuotaConfig {

  /** "Today" for caps and the spend budget; replaced in tests. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
