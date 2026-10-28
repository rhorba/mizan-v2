package ma.mizan.api.quota;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Today's global cap as metrics (Story 6.1): the cost alert fires above 80 % of the cap
 * (docs/devops §6). Read on every metrics push, one indexed row per read.
 */
@Component
class QuotaMetrics implements MeterBinder {

  private final DailyUsage usage;
  private final QuotaProperties limits;

  QuotaMetrics(DailyUsage usage, QuotaProperties limits) {
    this.usage = usage;
    this.limits = limits;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("mizan.quota.global.used", usage, u -> u.globalToday().analyses())
        .description("Analyses all users took today against the global cap")
        .strongReference(true)
        .register(registry);
    Gauge.builder("mizan.quota.global.cap", limits, QuotaProperties::dailyGlobal)
        .description("Analyses all users together may run per day")
        .strongReference(true)
        .register(registry);
  }
}
