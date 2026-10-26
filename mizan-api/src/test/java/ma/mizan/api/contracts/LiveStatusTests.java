package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.mizan.api.MizanApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Story 3.4 / ADR-7: ANALYZING, the status broadcast, and the owner-checked SSE stream. */
@SpringBootTest(classes = MizanApiApplication.class)
@AutoConfigureMockMvc
class LiveStatusTests {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcClient jdbc;
  @Autowired AnalysisResultService results;
  @Autowired KafkaListenerEndpointRegistry listeners;

  UUID contractId;
  UUID analysisId;

  @BeforeEach
  void pendingAnalysisOfAlice() {
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
                + " VALUES (?, ?, 'fr', 'PENDING')")
        .params(analysisId, contractId)
        .update();
  }

  @Test
  void startedMovesPendingToAnalyzingOnceAndNeverBackwards() {
    assertThat(results.start(started())).isTrue();
    assertThat(status()).isEqualTo("ANALYZING");
    assertThat(results.start(started())).isFalse(); // Kafka retry of the analyzer

    results.complete(
        new AnalysisResults.Completed(
            1, analysisId, contractId, "Résumé", List.of(), "claude-opus-5-5", 10, 10));
    assertThat(results.start(started())).isFalse(); // late, after the result
    assertThat(status()).isEqualTo("COMPLETE");
    assertThat(results.start(new AnalysisResults.Started(1, analysisId, UUID.randomUUID(), null)))
        .isFalse();
  }

  @Test
  void ownerStreamGetsTheCurrentStatusThenEveryChangeThroughKafka() throws Exception {
    MvcResult stream =
        mockMvc
            .perform(get("/api/v1/contracts/{id}/events", contractId).with(as("alice")))
            .andReturn();

    assertThat(stream.getRequest().isAsyncStarted()).isTrue();
    assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    await().atMost(Duration.ofSeconds(5)).until(() -> body(stream).contains("PENDING"));
    assertThat(body(stream)).contains("event:status").contains(analysisId.toString());

    // Outbox → contract.analysis-status → this instance's consumer → the open stream.
    results.start(started());
    await().atMost(Duration.ofSeconds(20)).until(() -> body(stream).contains("ANALYZING"));

    results.fail(new AnalysisResults.Failed(2, analysisId, contractId, "model-unavailable", 0, 0));
    await().atMost(Duration.ofSeconds(20)).until(() -> body(stream).contains("FAILED"));
    // No contract text or owner ever goes down the stream.
    assertThat(body(stream)).doesNotContain("Article 1.").doesNotContain("alice");
  }

  @Test
  void anotherUserCannotOpenTheStreamAndNobodyWithoutAToken() throws Exception {
    MvcResult bob =
        mockMvc
            .perform(get("/api/v1/contracts/{id}/events", contractId).with(as("bob")))
            .andReturn();
    assertThat(bob.getRequest().isAsyncStarted()).isFalse();
    assertThat(bob.getResponse().getStatus()).isEqualTo(404);
    assertThat(bob.getResponse().getContentAsString())
        .contains("urn:mizan:problem:contract-not-found");

    MvcResult anonymous =
        mockMvc.perform(get("/api/v1/contracts/{id}/events", contractId)).andReturn();
    assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void eachInstanceConsumesTheStatusTopicWithItsOwnGroup() {
    MessageListenerContainer status =
        listeners.getListenerContainers().stream()
            .filter(
                c ->
                    c.getContainerProperties().getTopics() != null
                        && List.of(c.getContainerProperties().getTopics())
                            .contains(AnalysisStatusChanged.TOPIC))
            .findFirst()
            .orElseThrow();

    // A shared group would split the partitions: browsers on other instances would miss changes.
    assertThat(status.getGroupId())
        .startsWith("mizan-api-status-")
        .hasSizeGreaterThan("mizan-api-status-".length() + 30);
  }

  private AnalysisResults.Started started() {
    return new AnalysisResults.Started(1, analysisId, contractId, Instant.now());
  }

  private String status() {
    return jdbc.sql("SELECT status FROM mizan.analyses WHERE id = ?")
        .param(analysisId)
        .query(String.class)
        .single();
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static RequestPostProcessor as(String subject) {
    return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_user"));
  }
}
