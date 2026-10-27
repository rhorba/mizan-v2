package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Story 4.1: the result of one analysis, for its owner only. */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class AnalysisViewTests {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired AnalysisResultService results;

  UUID contractId;
  UUID analysisId;

  @BeforeEach
  void completedAnalysisOfAlice() {
    jdbc.sql("DELETE FROM mizan.contracts").update();
    contractId = UUID.randomUUID();
    analysisId = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO mizan.contracts (id, owner_sub, file_name, page_count, text_content)"
                + " VALUES (?, 'alice', 'bail.pdf', 1, 'Article 1.')")
        .param(contractId)
        .update();
    jdbc.sql(
            "INSERT INTO mizan.analyses (id, contract_id, language, status)"
                + " VALUES (?, ?, 'ar', 'ANALYZING')")
        .params(analysisId, contractId)
        .update();
  }

  @Test
  void ownerGetsSummaryAndFlagsInTheModelsOrderWithoutModelOrTokens() throws Exception {
    results.complete(
        new AnalysisResults.Completed(
            1,
            analysisId,
            contractId,
            "ملخص العقد.",
            List.of(
                new AnalysisResults.Flag("بند الغرامة", RiskLevel.HIGH, "بدون سقف.", "حدد سقفا."),
                new AnalysisResults.Flag("Paiement à 60 jours", RiskLevel.LOW, "Délai légal.", "")),
            "claude-opus-5-5",
            1358,
            1823));

    MvcTestResult result = get("alice", contractId, analysisId);

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id":"%s","language":"ar","status":"COMPLETE","summary":"ملخص العقد.",
             "failureReason":null,
             "flags":[
               {"position":0,"clauseText":"بند الغرامة","riskLevel":"HIGH",
                "explanation":"بدون سقف.","suggestedCorrection":"حدد سقفا."},
               {"position":1,"clauseText":"Paiement à 60 jours","riskLevel":"LOW",
                "explanation":"Délai légal.","suggestedCorrection":null}]}
            """
                .formatted(analysisId));
    assertThat(result).bodyJson().extractingPath("$.completedAt").isNotNull();
    assertThat(result).bodyJson().extractingPath("$.requestedAt").isNotNull();
    String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("claude-opus").doesNotContain("1358").doesNotContain("alice");
  }

  @Test
  void analysisInProgressHasAStatusAndNoFlagsYet() {
    assertThat(get("alice", contractId, analysisId))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":"ANALYZING","summary":null,"flags":[]}
            """);
  }

  @Test
  void anotherUserOrAnotherContractsAnalysisIsNotFound() {
    assertThat(get("bob", contractId, analysisId))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:contract-not-found");
    assertThat(get("alice", contractId, UUID.randomUUID()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:mizan:problem:analysis-not-found");
  }

  private MvcTestResult get(String subject, UUID contract, UUID analysis) {
    return mvc.get()
        .uri("/api/v1/contracts/{id}/analyses/{analysisId}", contract, analysis)
        .with(
            jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user")))
        .exchange();
  }
}
