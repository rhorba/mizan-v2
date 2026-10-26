package ma.mizan.analyzer;

import java.util.List;

/** ADR-6: the shape the model must answer in (enforced by the API's structured output). */
public record AnalysisOutput(String summary, List<ClauseFlag> flags) {}
