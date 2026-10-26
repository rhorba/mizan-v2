package ma.mizan.api.admin;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ma.mizan.api.contracts.AnalysisRetry;
import ma.mizan.api.contracts.AnalysisStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dead-letter screen (Sprint 3 🔴). Role {@code admin} is enforced for /api/v1/admin/** by
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin")
class DeadLetterController {

  private final DeadLetterReader reader;
  private final AnalysisRetry retries;

  DeadLetterController(DeadLetterReader reader, AnalysisRetry retries) {
    this.reader = reader;
    this.retries = retries;
  }

  @GetMapping("/dead-letters")
  List<DeadLetter> deadLetters() {
    List<DeadLetter> letters = reader.read();
    Map<UUID, AnalysisStatus> statuses =
        retries.statuses(
            letters.stream().map(DeadLetter::analysisId).filter(Objects::nonNull).toList());
    return letters.stream().map(d -> d.withStatus(statuses.get(d.analysisId()))).toList();
  }

  @PostMapping("/analyses/{id}/retry")
  ResponseEntity<?> retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt admin) {
    return switch (retries.retry(id, admin.getSubject())) {
      case REQUEUED -> ResponseEntity.accepted().build();
      case NOT_FOUND ->
          problem(HttpStatus.NOT_FOUND, "analysis-not-found", "No such analysis (deleted?).");
      case NOT_FAILED ->
          problem(HttpStatus.CONFLICT, "analysis-not-failed", "Only a FAILED analysis is retried.");
    };
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String slug, String title) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create("urn:mizan:problem:" + slug));
    problem.setTitle(title);
    return ResponseEntity.status(status).body(problem);
  }
}
