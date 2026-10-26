package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Per-user daily spend budget (Sprint 3 🔴): default $0.50 at Opus 5.5 prices. */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class SpendBudgetTests {

  static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired SpendBudget budget;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
  }

  @Test
  void tokenSpendIsPricedPerMillionTokens() {
    BudgetProperties opus =
        new BudgetProperties(
            new BigDecimal("0.50"), new BigDecimal("4"), new BigDecimal("20"), CASABLANCA);

    // The live Opus run: 1358 in, 1823 out.
    assertThat(new TokenSpend(1358, 1823).usd(opus)).isEqualByComparingTo("0.041892");
    assertThat(new TokenSpend(0, 0).usd(opus)).isEqualByComparingTo("0");
  }

  @Test
  void userUnderBudgetCanUpload() {
    analysis("user-a", startOfToday().plusSeconds(60), 10_000, 10_000); // $0.24

    assertThat(upload("user-a")).hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  void userAtBudgetIsRefusedBeforeAnythingIsStored() {
    analysis("user-a", startOfToday().plusSeconds(60), 25_000, 20_000); // $0.50

    MvcTestResult result = upload("user-a");

    assertThat(result)
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"type":"urn:mizan:problem:budget-exceeded","status":429}
            """);
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.contracts").query(Long.class).single())
        .isEqualTo(1);
    // Another user's budget is their own.
    assertThat(upload("user-b")).hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  void spendBeforeTodayInCasablancaDoesNotCount() {
    analysis("user-a", startOfToday().minusSeconds(1), 1_000_000, 1_000_000);

    assertThat(budget.spentToday("user-a")).isEqualByComparingTo("0");
    assertThat(upload("user-a")).hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  void analysesWithoutTokensYetCostNothing() {
    analysis("user-a", Instant.now(), null, null);

    assertThat(budget.spentToday("user-a")).isEqualByComparingTo("0");
  }

  private static Instant startOfToday() {
    return LocalDate.now(CASABLANCA).atStartOfDay(CASABLANCA).toInstant();
  }

  private void analysis(String owner, Instant requestedAt, Integer input, Integer output) {
    UUID contract = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, ?, 'old.pdf', 1, 'Article 1.')")
        .params(contract, owner)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status, input_tokens,"
                + " output_tokens, requested_at) VALUES (?, ?, 'fr', 'COMPLETE', ?, ?, ?)")
        .params(UUID.randomUUID(), contract, input, output, Timestamp.from(requestedAt))
        .update();
  }

  private MvcTestResult upload(String subject) {
    return mvc.post()
        .uri("/api/v1/contracts")
        .multipart()
        .file(new MockMultipartFile("file", "bail.pdf", "application/pdf", PdfFixtures.textPdf(1)))
        .param("language", "fr")
        .with(
            jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user")))
        .exchange();
  }
}
