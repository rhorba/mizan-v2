package ma.mizan.api.contracts;

import java.net.URI;
import org.springframework.http.HttpStatus;

/** Non-upload problems of the contracts API; same URN scheme as {@link UploadProblem}. */
enum ContractProblem {
  NOT_FOUND("contract-not-found", HttpStatus.NOT_FOUND, "No such contract."),
  ANALYSIS_NOT_FOUND("analysis-not-found", HttpStatus.NOT_FOUND, "No such analysis."),
  ANALYSIS_IN_PROGRESS(
      "analysis-in-progress",
      HttpStatus.CONFLICT,
      "An analysis of this contract is still running."),
  ALREADY_ANALYZED(
      "already-analyzed",
      HttpStatus.CONFLICT,
      "This contract is already analyzed in that language."),
  BAD_CURSOR("bad-cursor", HttpStatus.BAD_REQUEST, "The history cursor is not valid.");

  private final String slug;
  private final HttpStatus status;
  private final String title;

  ContractProblem(String slug, HttpStatus status, String title) {
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
