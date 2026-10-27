package ma.mizan.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Authorization rules, response format and headers, with mocked tokens (no Keycloak). */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class SecurityRulesTests {

  @Autowired MockMvcTester mvc;

  static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_user");
  static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_admin");

  @Test
  void quotaReturnsDailyCapForSignedInUser() {
    assertThat(mvc.get().uri("/api/v1/me/quota").with(jwt().authorities(USER)))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"limit\":1000,\"used\":0}"); // test config caps
  }

  @Test
  void missingTokenGets401ProblemDetailsWithBearerChallenge() {
    MvcTestResult result = mvc.get().uri("/api/v1/me/quota").exchange();
    assertThat(result)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo("{\"status\":401,\"instance\":\"/api/v1/me/quota\"}");
    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
  }

  @Test
  void userOnAdminGets403ProblemDetails() {
    assertThat(mvc.get().uri("/api/v1/admin/stats").with(jwt().authorities(USER)))
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo("{\"status\":403,\"instance\":\"/api/v1/admin/stats\"}");
  }

  @Test
  void adminPassesAdminRule() {
    assertThat(mvc.get().uri("/api/v1/admin/stats").with(jwt().authorities(USER, ADMIN)))
        .hasStatus(HttpStatus.OK);
  }

  @Test
  void pathsOutsideTheApiAreDenied() {
    assertThat(mvc.get().uri("/actuator/env").with(jwt().authorities(ADMIN)))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void protectedResourceMetadataIsPublished() {
    assertThat(mvc.get().uri("/.well-known/oauth-protected-resource"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.authorization_servers[0]")
        .isEqualTo("http://localhost:8180/realms/mizan");
  }

  @Test
  void healthIsPublic() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
  }

  @Test
  void corsPreflightAllowsConfiguredOrigin() {
    assertThat(preflight("http://localhost:4200"))
        .hasStatusOk()
        .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");
  }

  @Test
  void corsPreflightRejectsOtherOrigins() {
    assertThat(preflight("https://evil.example")).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void responsesCarrySecurityHeaders() {
    assertThat(mvc.get().uri("/api/v1/me/quota").with(jwt().authorities(USER)))
        .hasHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        .hasHeader("X-Content-Type-Options", "nosniff")
        .hasHeader("Referrer-Policy", "no-referrer");
  }

  private MockMvcTester.MockMvcRequestBuilder preflight(String origin) {
    return mvc.options()
        .uri("/api/v1/me/quota")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization");
  }
}
