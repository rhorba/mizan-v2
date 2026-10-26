package ma.mizan.api.contracts;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  UploadProperties.class,
  AntivirusProperties.class,
  BudgetProperties.class
})
class ContractsWebConfig {

  /** "Today" for the spend budget; replaced in tests. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** After Spring Security (order -100), so the subject is known; before MVC parses multipart. */
  @Bean
  FilterRegistrationBean<UploadRateLimitFilter> uploadRateLimit(
      UploadProperties limits, JsonMapper json) {
    FilterRegistrationBean<UploadRateLimitFilter> registration =
        new FilterRegistrationBean<>(new UploadRateLimitFilter(limits.uploadsPerMinute(), json));
    registration.setOrder(0);
    registration.addUrlPatterns("/api/v1/contracts");
    return registration;
  }

  /** RFC 9457 bodies for refused uploads; the type URI is what the SPA keys on. */
  @RestControllerAdvice
  static class UploadProblems {

    @ExceptionHandler(UploadRejectedException.class)
    ProblemDetail rejected(UploadRejectedException e) {
      return toProblem(e.problem());
    }

    @ExceptionHandler(ContractProblemException.class)
    ProblemDetail contractProblem(ContractProblemException e) {
      ContractProblem problem = e.problem();
      ProblemDetail detail = ProblemDetail.forStatusAndDetail(problem.status(), problem.title());
      detail.setType(problem.type());
      detail.setTitle(problem.title());
      return detail;
    }

    /** Raised by the servlet container before any controller runs (file > multipart limit). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
      return toProblem(UploadProblem.TOO_LARGE);
    }

    static ProblemDetail toProblem(UploadProblem problem) {
      ProblemDetail detail = ProblemDetail.forStatusAndDetail(problem.status(), problem.title());
      detail.setType(problem.type());
      detail.setTitle(problem.title());
      return detail;
    }
  }
}
