package ma.mizan.api.contracts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PDFs built in memory for tests, so every edge case is explicit and nothing binary is committed.
 */
final class PdfFixtures {

  static final String CLAUSE =
      "Article %d. Le prestataire s'engage a livrer le travail dans les delais convenus.";

  private PdfFixtures() {}

  static byte[] textPdf(int pages) {
    return build(
        doc -> {
          for (int i = 1; i <= pages; i++) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(doc, page)) {
              text.beginText();
              text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
              text.newLineAtOffset(50, 750);
              text.showText(CLAUSE.formatted(i));
              text.endText();
            }
          }
        });
  }

  /** What a scanner produces: pages with graphics and no text layer. */
  static byte[] scannedPdf(int pages) {
    return build(
        doc -> {
          for (int i = 0; i < pages; i++) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream ink = new PDPageContentStream(doc, page)) {
              ink.addRect(50, 50, 400, 600);
              ink.fill();
            }
          }
        });
  }

  static byte[] blankPages(int pages) {
    return build(
        doc -> {
          for (int i = 0; i < pages; i++) {
            doc.addPage(new PDPage(new PDRectangle(10, 10)));
          }
        });
  }

  static byte[] encryptedPdf(String userPassword) {
    byte[] plain = textPdf(1);
    try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(plain)) {
      StandardProtectionPolicy policy =
          new StandardProtectionPolicy("owner-secret", userPassword, new AccessPermission());
      policy.setEncryptionKeyLength(128);
      doc.protect(policy);
      return save(doc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private interface Content {
    void addTo(PDDocument doc) throws IOException;
  }

  private static byte[] build(Content content) {
    try (PDDocument doc = new PDDocument()) {
      content.addTo(doc);
      return save(doc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] save(PDDocument doc) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    doc.save(out);
    return out.toByteArray();
  }
}
