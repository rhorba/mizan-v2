package ma.mizan.api.contracts;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ClamAV daemon, bound from {@code mizan.antivirus.*}. With no host, uploads are not scanned and a
 * warning is logged at start-up (owner decision, Sprint 2); with a host, an unreachable daemon
 * refuses the upload instead of silently skipping the scan.
 */
@ConfigurationProperties("mizan.antivirus")
record AntivirusProperties(
    String host,
    @DefaultValue("3310") int port,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout) {

  boolean enabled() {
    return host != null && !host.isBlank();
  }
}
