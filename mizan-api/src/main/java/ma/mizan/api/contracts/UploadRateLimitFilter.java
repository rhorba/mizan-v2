package ma.mizan.api.contracts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Per-user upload rate limit (token bucket, in memory: one API instance). Runs after Spring
 * Security, so the subject is known, and before the multipart body is parsed by Spring MVC.
 */
class UploadRateLimitFilter extends OncePerRequestFilter {

  static final URI TYPE = URI.create("urn:mizan:problem:rate-limited");

  private final int perMinute;
  private final JsonMapper json;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

  UploadRateLimitFilter(int perMinute, JsonMapper json) {
    this.perMinute = perMinute;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equals(request.getMethod())
        && "/api/v1/contracts".equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      chain.doFilter(request, response); // security already answered 401
      return;
    }
    ConsumptionProbe probe =
        buckets.get(auth.getName(), k -> newBucket()).tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      response.setHeader("RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
      chain.doFilter(request, response);
      return;
    }
    long retryAfter =
        Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Too many uploads. Try again in a moment.");
    problem.setType(TYPE);
    problem.setTitle("Too many uploads.");
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1))
                .build())
        .build();
  }
}
