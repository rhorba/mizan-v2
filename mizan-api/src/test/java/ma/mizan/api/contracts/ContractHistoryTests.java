package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Story 4.2: history (keyset pages of 20), one contract, delete. */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class ContractHistoryTests {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
  }

  @Test
  void twentyFiveContractsArriveAsTwentyNewestThenFive() {
    List<UUID> newestFirst = insertContracts("user-a", 25, Instant.now()).reversed();

    MvcTestResult first = get("user-a", "/api/v1/contracts");
    assertThat(first).hasStatusOk();
    List<String> firstIds = ids(first);
    String cursor = JsonPath.read(body(first), "$.nextCursor");
    assertThat(firstIds).hasSize(20).containsExactlyElementsOf(strings(newestFirst.subList(0, 20)));
    assertThat(cursor).isNotBlank();

    MvcTestResult second = get("user-a", "/api/v1/contracts?cursor=" + cursor);
    assertThat(ids(second)).containsExactlyElementsOf(strings(newestFirst.subList(20, 25)));
    assertThat((Object) JsonPath.read(body(second), "$.nextCursor")).isNull();
  }

  @Test
  void contractsWithTheSameTimestampAreNeitherSkippedNorRepeated() {
    Instant sameMoment = Instant.now().truncatedTo(ChronoUnit.MICROS);
    List<UUID> all = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      all.add(insertContract("user-a", sameMoment));
    }
    MvcTestResult first = get("user-a", "/api/v1/contracts");
    String cursor = JsonPath.read(body(first), "$.nextCursor");
    List<String> seen = new ArrayList<>(ids(first));
    seen.addAll(ids(get("user-a", "/api/v1/contracts?cursor=" + cursor)));
    assertThat(seen)
        .hasSize(21)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(strings(all));
  }

  @Test
  void eachRowCarriesItsLatestAnalysis() {
    UUID id = insertContract("user-a", Instant.now());
    insertAnalysis(id, "fr", "COMPLETE", Instant.now().minusSeconds(60));
    insertAnalysis(id, "ar", "ANALYZING", Instant.now());

    assertThat(get("user-a", "/api/v1/contracts"))
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"items\":[{\"id\":\"%s\",\"analysis\":{\"language\":\"ar\",\"status\":\"ANALYZING\"}}]}"
                .formatted(id));
    assertThat(get("user-a", "/api/v1/contracts/" + id))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"analysis\":{\"language\":\"ar\",\"status\":\"ANALYZING\"}}");
  }

  @Test
  void emptyHistory() {
    assertThat(get("user-a", "/api/v1/contracts"))
        .bodyJson()
        .isLenientlyEqualTo("{\"items\":[],\"nextCursor\":null}");
  }

  @Test
  void aContractWithoutAnalysisShowsNone() {
    UUID id = insertContract("user-a", Instant.now());
    assertThat(get("user-a", "/api/v1/contracts/" + id))
        .bodyJson()
        .isLenientlyEqualTo("{\"analysis\":null}");
  }

  @Test
  void deleteRemovesTheContractAndItsAnalysesThenGetIs404() {
    UUID id = insertContract("user-a", Instant.now());
    insertAnalysis(id, "fr", "PENDING", Instant.now());

    assertThat(mvc.delete().uri("/api/v1/contracts/" + id).with(user("user-a")))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(get("user-a", "/api/v1/contracts/" + id))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .isLenientlyEqualTo("{\"type\":\"urn:mizan:problem:contract-not-found\"}");
    assertThat(count("analyses")).isZero();
    assertThat(mvc.delete().uri("/api/v1/contracts/" + id).with(user("user-a")))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void tamperedOrForeignCursorsAreRejected() {
    for (String cursor : List.of("garbage", "MTIz", "YWJjfGRlZg", "fHw")) {
      assertThat(get("user-a", "/api/v1/contracts?cursor=" + cursor))
          .hasStatus(HttpStatus.BAD_REQUEST)
          .bodyJson()
          .isLenientlyEqualTo("{\"type\":\"urn:mizan:problem:bad-cursor\"}");
    }
  }

  @Test
  void malformedIdIsABadRequest() {
    assertThat(get("user-a", "/api/v1/contracts/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  // --- helpers ---

  private List<UUID> insertContracts(String owner, int n, Instant start) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ids.add(insertContract(owner, start.plusSeconds(i)));
    }
    return ids;
  }

  UUID insertContract(String owner, Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content, created_at)"
                + " VALUES (?, ?, 'c.pdf', 1, 'text', ?)")
        .params(id, owner, java.sql.Timestamp.from(createdAt.truncatedTo(ChronoUnit.MICROS)))
        .update();
    return id;
  }

  private void insertAnalysis(UUID contractId, String language, String status, Instant at) {
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status, requested_at)"
                + " VALUES (gen_random_uuid(), ?, ?, ?, ?)")
        .params(contractId, language, status, java.sql.Timestamp.from(at))
        .update();
  }

  private long count(String table) {
    return jdbc.sql("SELECT count(*) FROM mizan." + table).query(Long.class).single();
  }

  private MvcTestResult get(String owner, String uri) {
    return mvc.get().uri(uri).with(user(owner)).exchange();
  }

  static org.springframework.test.web.servlet.request.RequestPostProcessor user(String sub) {
    return jwt().jwt(j -> j.subject(sub));
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> ids(MvcTestResult result) {
    return JsonPath.read(body(result), "$.items[*].id");
  }

  private static List<String> strings(List<UUID> ids) {
    return ids.stream().map(UUID::toString).toList();
  }
}
