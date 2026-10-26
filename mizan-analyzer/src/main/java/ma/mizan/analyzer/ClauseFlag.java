package ma.mizan.analyzer;

/** One risky clause. {@code suggestedCorrection} is empty when none is proposed. */
public record ClauseFlag(
    String clauseText, RiskLevel riskLevel, String explanation, String suggestedCorrection) {}
