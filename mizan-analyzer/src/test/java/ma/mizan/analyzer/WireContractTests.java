package ma.mizan.analyzer;

import org.junit.jupiter.api.Test;

/** Sprint 3 (🔴): the analyzer's messages match the shared Kafka schemas. */
class WireContractTests {

  @Test
  void analysisRequestedIsReadWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-requested.v1.schema.json", AnalysisRequest.class);
  }

  @Test
  void analysisCompletedIsWrittenWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-completed.v1.schema.json", AnalysisCompleted.class);
  }

  @Test
  void analysisFailedIsWrittenWithTheSharedSchema() throws Exception {
    WireSchemas.assertMatches("analysis-failed.v2.schema.json", AnalysisFailed.class);
  }
}
