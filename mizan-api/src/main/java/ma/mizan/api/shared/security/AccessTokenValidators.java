package ma.mizan.api.shared.security;

import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** Checks every access token for expiry, issuer and audience (docs/security §2, Tampering). */
final class AccessTokenValidators {

  static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  private AccessTokenValidators() {}

  static OAuth2TokenValidator<Jwt> create(String issuerUri, String audience) {
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(CLOCK_SKEW),
        new JwtIssuerValidator(issuerUri),
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)));
  }
}
