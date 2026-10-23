package ma.mizan.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRolesConverterTests {

  final KeycloakRealmRolesConverter converter = new KeycloakRealmRolesConverter();

  @Test
  void mapsRealmRolesToSpringRoles() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("user", "admin"))));
    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_user", "ROLE_admin");
  }

  @Test
  void ignoresNonStringRoles() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("user", 42))));
    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_user");
  }

  @Test
  void grantsNothingWithoutRealmAccess() {
    assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
  }

  @Test
  void grantsNothingWhenRolesAreMalformed() {
    assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "admin"))))).isEmpty();
  }

  private static Jwt jwt(Map<String, Object> claims) {
    return Jwt.withTokenValue("t").header("alg", "RS256").claims(c -> c.putAll(claims)).build();
  }
}
