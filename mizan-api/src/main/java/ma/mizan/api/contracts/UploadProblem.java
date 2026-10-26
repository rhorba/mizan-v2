package ma.mizan.api.contracts;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * Why an upload was refused (FR-2). The {@code type} URI is a stable contract with the SPA, which
 * maps the slug to a localized message; titles are for humans reading raw responses.
 */
enum UploadProblem {
  NOT_A_PDF("not-a-pdf", HttpStatus.BAD_REQUEST, "The file is not a PDF."),
  TOO_LARGE("too-large", HttpStatus.CONTENT_TOO_LARGE, "The file is larger than 10 MB."),
  TOO_MANY_PAGES("too-many-pages", HttpStatus.BAD_REQUEST, "The PDF has more than 30 pages."),
  NO_TEXT_LAYER(
      "no-text-layer", HttpStatus.BAD_REQUEST, "The PDF has no text layer (scanned document)."),
  ENCRYPTED("encrypted", HttpStatus.BAD_REQUEST, "The PDF is password-protected."),
  UNREADABLE("unreadable", HttpStatus.BAD_REQUEST, "The PDF could not be read."),
  BAD_LANGUAGE("bad-language", HttpStatus.BAD_REQUEST, "Language must be fr, ar or ary."),
  MALWARE_DETECTED(
      "malware-detected",
      HttpStatus.UNPROCESSABLE_CONTENT,
      "The file was flagged by the virus scan."),
  SCANNER_UNAVAILABLE(
      "scanner-unavailable",
      HttpStatus.SERVICE_UNAVAILABLE,
      "The virus scan is unavailable. Try again later."),
  BUDGET_EXCEEDED(
      "budget-exceeded",
      HttpStatus.TOO_MANY_REQUESTS,
      "Today's analysis budget is used up. Try again tomorrow.");

  private final String slug;
  private final HttpStatus status;
  private final String title;

  UploadProblem(String slug, HttpStatus status, String title) {
    this.slug = slug;
    this.status = status;
    this.title = title;
  }

  URI type() {
    return URI.create("urn:mizan:problem:" + slug);
  }

  HttpStatus status() {
    return status;
  }

  String title() {
    return title;
  }
}
