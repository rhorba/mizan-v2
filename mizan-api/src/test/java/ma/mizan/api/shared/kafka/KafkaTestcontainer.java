package ma.mizan.api.shared.kafka;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * One Kafka broker per test JVM (same image as compose), wired into every Spring Boot test context
 * through META-INF/spring.factories so existing tests need no annotation.
 */
public class KafkaTestcontainer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

  public static KafkaContainer container() {
    if (!KAFKA.isRunning()) {
      KAFKA.start();
    }
    return KAFKA;
  }

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "kafkaTestcontainer",
                Map.of("spring.kafka.bootstrap-servers", container().getBootstrapServers())));
  }
}
