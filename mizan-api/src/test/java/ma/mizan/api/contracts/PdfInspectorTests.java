package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** FR-2 and the test strategy's input-abuse list, without Spring. */
class PdfInspectorTests {

  final PdfInspector inspector =
      new PdfInspector(new UploadProperties(10 * 1024 * 1024, 30, 20, 16 * 1024 * 1024, 10));

  @Test
  void acceptsAThreePageTextPdfAndExtractsTheText() {
    PdfInspector.Inspection result = inspector.inspect(PdfFixtures.textPdf(3));
    assertThat(result.pageCount()).isEqualTo(3);
    assertThat(result.text()).contains("Article 1.", "Article 3.", "prestataire");
  }

  @Test
  void acceptsExactlyThirtyPages() {
    assertThat(inspector.inspect(PdfFixtures.textPdf(30)).pageCount()).isEqualTo(30);
  }

  @Test
  void rejectsThirtyOnePages() {
    assertRejected(PdfFixtures.textPdf(31), UploadProblem.TOO_MANY_PAGES);
  }

  @Test
  void rejectsTenThousandTinyPagesBeforeExtractingAnything() {
    assertRejected(PdfFixtures.blankPages(10_000), UploadProblem.TOO_MANY_PAGES);
  }

  @Test
  void rejectsAScannedPdf() {
    assertRejected(PdfFixtures.scannedPdf(3), UploadProblem.NO_TEXT_LAYER);
  }

  @Test
  void rejectsAPdfWithAUserPassword() {
    assertRejected(PdfFixtures.encryptedPdf("user-secret"), UploadProblem.ENCRYPTED);
  }

  @Test
  void rejectsAPdfThatIsEncryptedEvenWithoutAUserPassword() {
    assertRejected(PdfFixtures.encryptedPdf(""), UploadProblem.ENCRYPTED);
  }

  @Test
  void rejectsARenamedExecutable() {
    byte[] exe = Arrays.copyOf("MZ\u0090\u0000\u0003".getBytes(StandardCharsets.ISO_8859_1), 4096);
    assertRejected(exe, UploadProblem.NOT_A_PDF);
  }

  @Test
  void rejectsAnEmptyFile() {
    assertRejected(new byte[0], UploadProblem.NOT_A_PDF);
  }

  @Test
  void rejectsAPdfHeaderFollowedByGarbage() {
    byte[] garbage = "%PDF-1.7\n this is not a PDF body".getBytes(StandardCharsets.US_ASCII);
    assertRejected(garbage, UploadProblem.UNREADABLE);
  }

  @Test
  void rejectsAValidPdfWithNoPages() {
    assertRejected(PdfFixtures.blankPages(0), UploadProblem.UNREADABLE);
  }

  @Test
  void rejectsMoreThanTheSizeLimit() {
    PdfInspector small = new PdfInspector(new UploadProperties(1000, 30, 20, 1024, 10));
    assertRejected(small, PdfFixtures.textPdf(3), UploadProblem.TOO_LARGE);
  }

  private void assertRejected(byte[] bytes, UploadProblem problem) {
    assertRejected(inspector, bytes, problem);
  }

  private static void assertRejected(PdfInspector inspector, byte[] bytes, UploadProblem problem) {
    assertThatThrownBy(() -> inspector.inspect(bytes))
        .isInstanceOfSatisfying(
            UploadRejectedException.class, e -> assertThat(e.problem()).isEqualTo(problem));
  }
}
