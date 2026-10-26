package ma.mizan.api.contracts;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Contract endpoints (Architecture §5). The owner is always the token's subject. */
@RestController
@RequestMapping("/api/v1/contracts")
class ContractController {

  private final ContractUploadService uploads;
  private final StatusStreams streams;
  private final ContractQueryService queries;

  ContractController(
      ContractUploadService uploads, ContractQueryService queries, StatusStreams streams) {
    this.streams = streams;
    this.uploads = uploads;
    this.queries = queries;
  }

  record AnalysisView(UUID id, Language language, AnalysisStatus status, Instant requestedAt) {
    static AnalysisView of(Analysis a) {
      return a == null ? null : new AnalysisView(a.id(), a.language(), a.status(), a.requestedAt());
    }
  }

  record ContractView(
      UUID id, String fileName, int pageCount, Instant createdAt, AnalysisView analysis) {
    static ContractView of(Contract c, Analysis latest) {
      return new ContractView(
          c.id(), c.fileName(), c.pageCount(), c.createdAt(), AnalysisView.of(latest));
    }
  }

  record HistoryView(List<ContractView> items, String nextCursor) {}

  /** Story 4.1: an analysis with its result. Model and token counts stay internal. */
  record ResultView(
      UUID id,
      Language language,
      AnalysisStatus status,
      String summary,
      String failureReason,
      Instant requestedAt,
      Instant completedAt,
      List<FlagView> flags) {
    static ResultView of(ContractQueryService.Result r) {
      Analysis a = r.analysis();
      return new ResultView(
          a.id(),
          a.language(),
          a.status(),
          a.summary(),
          a.failureReason(),
          a.requestedAt(),
          a.completedAt(),
          r.flags().stream().map(FlagView::of).toList());
    }
  }

  record FlagView(
      int position,
      String clauseText,
      RiskLevel riskLevel,
      String explanation,
      String suggestedCorrection) {
    static FlagView of(ClauseFlag f) {
      return new FlagView(
          f.position(), f.clauseText(), f.riskLevel(), f.explanation(), f.suggestedCorrection());
    }
  }

  /** FR-2/FR-3: 202 because the analysis itself happens asynchronously (Epic 3). */
  @PostMapping(consumes = "multipart/form-data")
  ResponseEntity<ContractView> upload(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("file") MultipartFile file,
      @RequestParam("language") String language)
      throws IOException {
    Language lang =
        Language.fromCode(language)
            .orElseThrow(() -> new UploadRejectedException(UploadProblem.BAD_LANGUAGE));
    Analysis analysis =
        uploads.upload(jwt.getSubject(), file.getOriginalFilename(), file.getBytes(), lang);
    Contract c = analysis.contract();
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/contracts/" + c.id()))
        .body(ContractView.of(c, analysis));
  }

  /** FR-6: newest first, 20 per page; pass nextCursor back to get the next page. */
  @GetMapping
  HistoryView history(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor) {
    ContractQueryService.Page page = queries.history(jwt.getSubject(), cursor);
    return new HistoryView(
        page.entries().stream().map(e -> ContractView.of(e.contract(), e.latest())).toList(),
        page.next() == null ? null : page.next().encode());
  }

  @GetMapping("/{id}")
  ContractView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    ContractQueryService.Entry entry = queries.get(jwt.getSubject(), id);
    return ContractView.of(entry.contract(), entry.latest());
  }

  /**
   * Story 3.4 / ADR-7: status changes of this contract's analyses as Server-Sent Events. Owner
   * checked like every contract endpoint (404 otherwise); the current status is sent first.
   */
  @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    ContractQueryService.Entry entry = queries.get(jwt.getSubject(), id);
    return streams.open(id, AnalysisView.of(entry.latest()));
  }

  @GetMapping("/{id}/analyses/{analysisId}")
  ResultView analysis(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID analysisId) {
    return ResultView.of(queries.result(jwt.getSubject(), id, analysisId));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    queries.delete(jwt.getSubject(), id);
    return ResponseEntity.noContent().build();
  }
}
