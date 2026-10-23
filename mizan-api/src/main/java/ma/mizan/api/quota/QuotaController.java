package ma.mizan.api.quota;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Analyses left today for the signed-in user. Counting usage arrives with Story 5.1. */
@RestController
@RequestMapping("/api/v1/me/quota")
class QuotaController {

  private final QuotaProperties properties;

  QuotaController(QuotaProperties properties) {
    this.properties = properties;
  }

  @GetMapping
  QuotaView quota() {
    int used = 0;
    return new QuotaView(properties.dailyPerUser(), used, properties.dailyPerUser() - used);
  }

  record QuotaView(int limit, int used, int remaining) {}
}
