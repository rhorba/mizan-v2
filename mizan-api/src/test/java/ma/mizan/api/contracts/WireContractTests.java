package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sprint 3 (🔴): the API's messages match the shared Kafka schemas. */
class WireContractTests {

  @Test
  void analysisRequestedIsWrittenWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-requested.v1.schema.json", AnalysisRequested.class);
  }

  @Test
  void analysisCompletedIsReadWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-completed.v1.schema.json", AnalysisResults.Completed.class);
  }

  @Test
  void analysisFailedIsReadWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-failed.v2.schema.json", AnalysisResults.Failed.class);
  }

  /** One renamed field, as a careless refactoring would do. */
  record DriftedFailed(int schemaVersion, UUID analysisId, UUID contractId, String reasonCode) {}

  @Test
  void aRenamedFieldBreaksTheContract() {
    assertThatThrownBy(
            () -> WireSchemas.assertMatches("analysis-failed.v2.schema.json", DriftedFailed.class))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("no longer matches analysis-failed.v2.schema.json");
  }
}
