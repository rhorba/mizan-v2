package ma.mizan.api.quota;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Analyses left today for the signed-in user (Story 5.1); also bounded by the global cap. */
@RestController
@RequestMapping("/api/v1/me/quota")
class QuotaController {

  private final DailyUsage usage;

  QuotaController(DailyUsage usage) {
    this.usage = usage;
  }

  @GetMapping
  QuotaView quota(@AuthenticationPrincipal Jwt jwt) {
    DailyUsage.Today today = usage.today(jwt.getSubject());
    return new QuotaView(today.limit(), today.used(), today.remaining());
  }

  record QuotaView(int limit, int used, int remaining) {}
}
