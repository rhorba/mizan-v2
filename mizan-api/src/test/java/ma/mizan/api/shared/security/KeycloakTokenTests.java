package ma.mizan.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Real Keycloak tokens against the API (docs/test-strategy §4, auth abuse). The realm is the one
 * the local stack imports; test-only clients with the password grant are added here and never exist
 * in the committed realm, whose web client only allows Authorization Code + PKCE.
 */
@Testcontainers
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class KeycloakTokenTests {

  static final String PASSWORD =
      "Long-enough-passw0rd"; // gitleaks:allow (throwaway user inside the test container)

  @Container
  static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
          .withRealmImportFile("keycloak/realm-mizan.json")
          .withCopyFileToContainer(
              MountableFile.forHostPath("../docker/keycloak/password-blacklist.txt"),
              "/opt/keycloak/data/password-blacklists/mizan.txt");

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    registry.add("mizan.security.issuer-uri", () -> realmUrl("mizan"));
    registry.add(
        "mizan.security.jwk-set-uri", () -> realmUrl("mizan") + "/protocol/openid-connect/certs");
  }

  @Autowired MockMvcTester mvc;

  @BeforeAll
  static void createTestClientsAndUsers() {
    Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
    RealmResource mizan = admin.realm("mizan");
    createPasswordGrantClient(mizan, "it-with-audience", true);
    createPasswordGrantClient(mizan, "it-without-audience", false);
    createUser(mizan, "user@mizan.test", List.of());
    createUser(mizan, "admin@mizan.test", List.of("admin"));

    RealmRepresentation other = new RealmRepresentation();
    other.setRealm("other");
    other.setEnabled(true);
    admin.realms().create(other);
    createPasswordGrantClient(admin.realm("other"), "it-with-audience", true);
    createUser(admin.realm("other"), "user@mizan.test", List.of());
  }

  @Test
  void userTokenReadsQuota() {
    assertThat(get("/api/v1/me/quota", token("mizan", "it-with-audience", "user@mizan.test")))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"limit\":1000,\"used\":0}"); // test config caps
  }

  @Test
  void noTokenIsUnauthorized() {
    assertThat(mvc.get().uri("/api/v1/me/quota")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tamperedTokenIsUnauthorized() {
    String token = token("mizan", "it-with-audience", "user@mizan.test");
    String tampered = token.substring(0, token.length() - 4) + "AAAA";
    assertThat(get("/api/v1/me/quota", tampered)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenFromAnotherRealmIsUnauthorized() {
    assertThat(get("/api/v1/me/quota", token("other", "it-with-audience", "user@mizan.test")))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenWithoutApiAudienceIsUnauthorized() {
    assertThat(get("/api/v1/me/quota", token("mizan", "it-without-audience", "user@mizan.test")))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void signedUpUserGetsUserRoleAndIsForbiddenOnAdmin() {
    assertThat(get("/api/v1/admin/stats", token("mizan", "it-with-audience", "user@mizan.test")))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void adminTokenPassesAdminRule() {
    assertThat(get("/api/v1/admin/stats", token("mizan", "it-with-audience", "admin@mizan.test")))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void blacklistedPasswordIsRejected() {
    CredentialRepresentation common = new CredentialRepresentation();
    common.setType(CredentialRepresentation.PASSWORD);
    common.setValue("Password123");
    common.setTemporary(false);
    String id =
        KEYCLOAK
            .getKeycloakAdminClient()
            .realm("mizan")
            .users()
            .search("user@mizan.test")
            .getFirst()
            .getId();
    assertThatThrownBy(
            () ->
                KEYCLOAK
                    .getKeycloakAdminClient()
                    .realm("mizan")
                    .users()
                    .get(id)
                    .resetPassword(common))
        .isInstanceOf(BadRequestException.class);
  }

  private MockMvcTester.MockMvcRequestBuilder get(String uri, String token) {
    return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static String token(String realm, String clientId, String username) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", clientId);
    form.add("username", username);
    form.add("password", PASSWORD);
    Map<?, ?> body =
        RestClient.create()
            .post()
            .uri(realmUrl(realm) + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    return (String) body.get("access_token");
  }

  private static String realmUrl(String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static void createPasswordGrantClient(
      RealmResource realm, String clientId, boolean withApiAudience) {
    ClientRepresentation client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setPublicClient(true);
    client.setDirectAccessGrantsEnabled(true);
    client.setStandardFlowEnabled(false);
    if (withApiAudience) {
      ProtocolMapperRepresentation audience = new ProtocolMapperRepresentation();
      audience.setName("audience-mizan-api");
      audience.setProtocol("openid-connect");
      audience.setProtocolMapper("oidc-audience-mapper");
      audience.setConfig(
          Map.of("included.custom.audience", "mizan-api", "access.token.claim", "true"));
      client.setProtocolMappers(List.of(audience));
    }
    try (Response response = realm.clients().create(client)) {
      assertThat(response.getStatus()).isEqualTo(201);
    }
  }

  private static void createUser(RealmResource realm, String email, List<String> extraRoles) {
    CredentialRepresentation password = new CredentialRepresentation();
    password.setType(CredentialRepresentation.PASSWORD);
    password.setValue(PASSWORD);
    password.setTemporary(false);
    UserRepresentation user = new UserRepresentation();
    user.setUsername(email);
    user.setEmail(email);
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmailVerified(true);
    user.setEnabled(true);
    user.setCredentials(List.of(password));
    String id;
    try (Response response = realm.users().create(user)) {
      assertThat(response.getStatus()).isEqualTo(201);
      String location = response.getLocation().getPath();
      id = location.substring(location.lastIndexOf('/') + 1);
    }
    if (!extraRoles.isEmpty()) {
      realm
          .users()
          .get(id)
          .roles()
          .realmLevel()
          .add(extraRoles.stream().map(r -> realm.roles().get(r).toRepresentation()).toList());
    }
  }
}
