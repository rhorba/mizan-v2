package ma.mizan.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTokenValidatorsTests {

  static final String ISSUER = "http://localhost:8180/realms/mizan";
  final OAuth2TokenValidator<Jwt> validator = AccessTokenValidators.create(ISSUER, "mizan-api");

  @Test
  void acceptsCurrentTokenFromIssuerForApi() {
    assertThat(validator.validate(token(ISSUER, List.of("mizan-api"), inSeconds(300))).hasErrors())
        .isFalse();
  }

  @Test
  void rejectsExpiredToken() {
    assertThat(validator.validate(token(ISSUER, List.of("mizan-api"), inSeconds(-60))).hasErrors())
        .isTrue();
  }

  @Test
  void toleratesSmallClockSkew() {
    assertThat(validator.validate(token(ISSUER, List.of("mizan-api"), inSeconds(-10))).hasErrors())
        .isFalse();
  }

  @Test
  void rejectsTokenFromAnotherRealm() {
    Jwt jwt = token("http://localhost:8180/realms/other", List.of("mizan-api"), inSeconds(300));
    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  @Test
  void rejectsTokenForAnotherAudience() {
    assertThat(validator.validate(token(ISSUER, List.of("account"), inSeconds(300))).hasErrors())
        .isTrue();
  }

  @Test
  void rejectsTokenWithoutAudience() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .issuedAt(inSeconds(-1))
            .expiresAt(inSeconds(300))
            .build();
    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  private static Jwt token(String issuer, List<String> audience, Instant expiresAt) {
    return Jwt.withTokenValue("t")
        .header("alg", "RS256")
        .issuer(issuer)
        .audience(audience)
        .issuedAt(expiresAt.minusSeconds(300))
        .expiresAt(expiresAt)
        .build();
  }

  private static Instant inSeconds(long seconds) {
    return Instant.now().plusSeconds(seconds);
  }
}
