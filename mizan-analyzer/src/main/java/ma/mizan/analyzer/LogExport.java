package ma.mizan.analyzer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Story 6.1: connects the OTEL appender of logback-spring.xml to Boot's OpenTelemetry SDK, so log
 * records reach Loki with their trace and span ids. Records logged before this runs are dropped.
 */
@Component
class LogExport implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  LogExport(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
