package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Map;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Story 2.1 acceptance at the HTTP level, against PostgreSQL. */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class ContractUploadTests {

  /**
   * U+202E RIGHT-TO-LEFT OVERRIDE, built from its code point so no raw bidi char sits in source.
   */
  static final char RLO = (char) 0x202E;

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
  }

  @Test
  void textPdfIsAcceptedWithAPendingAnalysisAndOnlyItsTextIsStored() {
    MvcTestResult result = upload("Bail" + RLO + " commercial.pdf", PdfFixtures.textPdf(3), "fr");

    assertThat(result)
        .hasStatus(HttpStatus.ACCEPTED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"fileName":"Bail commercial.pdf","pageCount":3,
             "analysis":{"language":"fr","status":"PENDING"}}
            """);
    String id = result.getResponse().getHeader("Location").replace("/api/v1/contracts/", "");

    Map<String, Object> row =
        jdbc.sql("SELECT owner_sub, text_content FROM mizan.contracts WHERE id = ?::uuid")
            .param(id)
            .query()
            .singleRow();
    assertThat(row.get("owner_sub")).isEqualTo("user-a");
    assertThat((String) row.get("text_content")).contains("Article 1.", "Article 3.");
    // ADR-5: no column anywhere in the schema can hold the PDF bytes.
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_schema = 'mizan' AND data_type = 'bytea'")
                .query(Long.class)
                .single())
        .isZero();
    assertThat((String) row.get("text_content")).doesNotContain("%PDF");
  }

  static Object[][] refusals() {
    return new Object[][] {
      {"virus.pdf", "MZ-not-a-pdf".getBytes(), "fr", "not-a-pdf", 400},
      {"long.pdf", PdfFixtures.textPdf(31), "fr", "too-many-pages", 400},
      {"scan.pdf", PdfFixtures.scannedPdf(2), "ar", "no-text-layer", 400},
      {"locked.pdf", PdfFixtures.encryptedPdf("secret"), "fr", "encrypted", 400},
      {"ok.pdf", PdfFixtures.textPdf(1), "en", "bad-language", 400},
    };
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("refusals")
  void invalidUploadsGetProblemDetailsAndStoreNothing(
      String name, byte[] bytes, String language, String slug, int status) {
    assertThat(upload(name, bytes, language))
        .hasStatus(status)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"type\":\"urn:mizan:problem:%s\",\"status\":%d}".formatted(slug, status));
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.contracts").query(Long.class).single())
        .isZero();
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.analyses").query(Long.class).single()).isZero();
  }

  @Test
  void elevenMegabytesIsRefusedWith413() {
    byte[] big = new byte[11 * 1024 * 1024];
    System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);
    assertThat(upload("big.pdf", big, "fr"))
        .hasStatus(HttpStatus.CONTENT_TOO_LARGE)
        .bodyJson()
        .isLenientlyEqualTo("{\"type\":\"urn:mizan:problem:too-large\"}");
  }

  @Test
  void containerSizeLimitAlsoMapsToTooLarge() {
    assertThat(
            new ContractsWebConfig.UploadProblems()
                .tooLarge(new MaxUploadSizeExceededException(10))
                .getType()
                .toString())
        .isEqualTo("urn:mizan:problem:too-large");
  }

  @Test
  void missingFileIsAProblemToo() {
    assertThat(
            mvc.post()
                .uri("/api/v1/contracts")
                .multipart()
                .param("language", "fr")
                .with(jwt().jwt(j -> j.subject("user-a"))))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void uploadRequiresSignIn() {
    assertThat(
            mvc.post()
                .uri("/api/v1/contracts")
                .multipart()
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[1]))
                .param("language", "fr"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MvcTestResult upload(String fileName, byte[] bytes, String language) {
    return mvc.post()
        .uri("/api/v1/contracts")
        .multipart()
        .file(new MockMultipartFile("file", fileName, "application/pdf", bytes))
        .param("language", language)
        .with(
            jwt()
                .jwt(j -> j.subject("user-a"))
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
        .exchange();
  }
}
