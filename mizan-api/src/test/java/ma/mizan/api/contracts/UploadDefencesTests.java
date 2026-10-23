package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** ClamAV (fake clamd) and the per-user rate limit, through the real HTTP stack. */
@SpringBootTest(
    classes = MizanApiApplication.class,
    properties = "mizan.upload.uploads-per-minute=3")
@AutoConfigureMockMvc
class UploadDefencesTests {

  static final FakeClamd CLAMD = startClamd();

  @DynamicPropertySource
  static void clamd(DynamicPropertyRegistry registry) {
    registry.add("mizan.antivirus.host", () -> "localhost");
    registry.add("mizan.antivirus.port", CLAMD::port);
  }

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
  }

  @AfterAll
  static void stopClamd() throws IOException {
    CLAMD.close();
  }

  @Test
  void infectedUploadIsRefusedWith422AndNothingIsStored() {
    byte[] infected = ("%PDF-1.7\n% " + FakeClamd.MARKER).getBytes(StandardCharsets.US_ASCII);
    assertThat(upload("mallory", "invoice.pdf", infected))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .isLenientlyEqualTo("{\"type\":\"urn:mizan:problem:malware-detected\"}");
    assertThat(jdbc.sql("SELECT count(*) FROM mizan.contracts").query(Long.class).single())
        .isZero();
  }

  @Test
  void cleanUploadsAreScannedThenAccepted() {
    int before = CLAMD.scans();
    assertThat(upload("clean-user", "ok.pdf", PdfFixtures.textPdf(2)))
        .hasStatus(HttpStatus.ACCEPTED);
    assertThat(CLAMD.scans()).isEqualTo(before + 1);
  }

  @Test
  void fourthUploadInAMinuteIsRateLimitedButOtherUsersAreNot() {
    for (int i = 0; i < 3; i++) {
      assertThat(upload("busy-user", "c" + i + ".pdf", PdfFixtures.textPdf(1)))
          .hasStatus(HttpStatus.ACCEPTED)
          .hasHeader("RateLimit-Remaining", Integer.toString(2 - i));
    }
    MvcTestResult limited = upload("busy-user", "c3.pdf", PdfFixtures.textPdf(1));
    assertThat(limited)
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .isLenientlyEqualTo("{\"type\":\"urn:mizan:problem:rate-limited\",\"status\":429}");
    assertThat(Long.parseLong(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
        .isBetween(1L, 61L);

    assertThat(upload("other-user", "x.pdf", PdfFixtures.textPdf(1)))
        .hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  void readsAreNotRateLimited() {
    for (int i = 0; i < 5; i++) {
      assertThat(mvc.get().uri("/api/v1/contracts").with(jwt().jwt(j -> j.subject("reader"))))
          .hasStatusOk();
    }
  }

  private MvcTestResult upload(String sub, String name, byte[] bytes) {
    return mvc.post()
        .uri("/api/v1/contracts")
        .multipart()
        .file(new MockMultipartFile("file", name, "application/pdf", bytes))
        .param("language", "fr")
        .with(jwt().jwt(j -> j.subject(sub)))
        .exchange();
  }

  private static FakeClamd startClamd() {
    try {
      return new FakeClamd();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
