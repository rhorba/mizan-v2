package ma.mizan.api.contracts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Validates an uploaded PDF and extracts its text (FR-2, ADR-5). Cheap checks run first and the
 * page count is checked before any text extraction, so a 10 000-page or garbage file costs little.
 */
@Component
class PdfInspector {

  private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final UploadProperties limits;

  PdfInspector(UploadProperties limits) {
    this.limits = limits;
  }

  record Inspection(int pageCount, String text) {}

  Inspection inspect(byte[] bytes) {
    if (bytes.length > limits.maxBytes()) {
      throw new UploadRejectedException(UploadProblem.TOO_LARGE);
    }
    if (bytes.length < MAGIC.length || !Arrays.equals(Arrays.copyOf(bytes, MAGIC.length), MAGIC)) {
      throw new UploadRejectedException(UploadProblem.NOT_A_PDF);
    }
    try (PDDocument pdf =
        Loader.loadPDF(
            bytes,
            "",
            null,
            null,
            MemoryUsageSetting.setupMixed(limits.pdfMainMemoryBytes()).streamCache)) {
      if (pdf.isEncrypted()) {
        // Opened with an empty user password but still encrypted (owner password only).
        throw new UploadRejectedException(UploadProblem.ENCRYPTED);
      }
      int pages = pdf.getNumberOfPages();
      if (pages == 0) {
        throw new UploadRejectedException(UploadProblem.UNREADABLE);
      }
      if (pages > limits.maxPages()) {
        throw new UploadRejectedException(UploadProblem.TOO_MANY_PAGES);
      }
      // PostgreSQL text cannot hold NUL characters.
      String text = new PDFTextStripper().getText(pdf).replace("\u0000", "");
      long visible = text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
      if (visible < (long) pages * limits.minCharsPerPage()) {
        throw new UploadRejectedException(UploadProblem.NO_TEXT_LAYER);
      }
      return new Inspection(pages, text);
    } catch (InvalidPasswordException e) {
      throw new UploadRejectedException(UploadProblem.ENCRYPTED);
    } catch (IOException e) {
      throw new UploadRejectedException(UploadProblem.UNREADABLE);
    }
  }
}
