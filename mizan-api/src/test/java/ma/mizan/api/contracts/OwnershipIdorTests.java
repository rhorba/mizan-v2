package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * NFR-2 / test strategy "Owner scoping — Maximum": user B attacks user A's contract on EVERY
 * /api/v1/contracts/{id}… endpoint. Endpoints are discovered from Spring's handler mappings, so a
 * future endpoint is covered without anyone remembering to add it here.
 */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class OwnershipIdorTests {

  static final String PREFIX = "/api/v1/contracts/{id}";

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  UUID aliceContract;
  UUID aliceAnalysis;

  @BeforeEach
  void aliceOwnsAContract() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    aliceContract = UUID.randomUUID();
    aliceAnalysis = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'alice', 'alice.pdf', 1, 'confidential clause')")
        .param(aliceContract)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                + " VALUES (?, ?, 'fr', 'PENDING')")
        .params(aliceAnalysis, aliceContract)
        .update();
  }

  @Test
  void everyContractEndpointWithAnIdHidesAlicesContractFromBob() {
    List<Map.Entry<HttpMethod, String>> endpoints = idEndpoints();
    assertThat(endpoints).as("discovered endpoints").isNotEmpty();

    for (Map.Entry<HttpMethod, String> endpoint : endpoints) {
      // Alice's real ids: the strongest case. A new path variable fails loudly until added here.
      String uri =
          endpoint
              .getValue()
              .replace("{id}", aliceContract.toString())
              .replace("{analysisId}", aliceAnalysis.toString());
      var response =
          mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                      endpoint.getKey(), uri)
                  .with(jwt().jwt(j -> j.subject("bob"))));
      assertThat(response)
          .as("%s %s as bob", endpoint.getKey(), endpoint.getValue())
          .hasStatus(404)
          .bodyText()
          .doesNotContain("alice.pdf", "confidential");
    }
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.contracts").query(Long.class).single())
        .as("nothing of Alice's was deleted or changed")
        .isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.analyses").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  void bobsHistoryNeverListsAlicesContract() {
    assertThat(mvc.get().uri("/api/v1/contracts").with(jwt().jwt(j -> j.subject("bob"))))
        .bodyJson()
        .isLenientlyEqualTo("{\"items\":[],\"nextCursor\":null}");
  }

  @Test
  void aliceStillSeesHerOwnContract() {
    assertThat(
            mvc.get()
                .uri("/api/v1/contracts/" + aliceContract)
                .with(jwt().jwt(j -> j.subject("alice"))))
        .hasStatusOk();
  }

  @Test
  void bobCannotTellForeignFromMissing() {
    String foreign =
        bodyOf("/api/v1/contracts/" + aliceContract).replace(aliceContract.toString(), "ID");
    UUID missing = UUID.randomUUID();
    String absent = bodyOf("/api/v1/contracts/" + missing).replace(missing.toString(), "ID");
    assertThat(foreign).isEqualTo(absent);
  }

  private String bodyOf(String uri) {
    try {
      return mvc.get()
          .uri(uri)
          .with(jwt().jwt(j -> j.subject("bob")))
          .exchange()
          .getResponse()
          .getContentAsString();
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<Map.Entry<HttpMethod, String>> idEndpoints() {
    return mappings.getHandlerMethods().keySet().stream()
        .flatMap(
            (RequestMappingInfo info) ->
                info.getPatternValues().stream()
                    .filter(p -> p.startsWith(PREFIX))
                    .flatMap(
                        p ->
                            info.getMethodsCondition().getMethods().stream()
                                .map(m -> Map.entry(HttpMethod.valueOf(m.name()), p))))
        .toList();
  }
}
