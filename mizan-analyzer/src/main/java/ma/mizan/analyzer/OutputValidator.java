package ma.mizan.analyzer;

import java.util.List;

/**
 * Checks what structured output cannot: limits that keep a bad or manipulated answer out of the
 * database (column sizes in docs/database, a sane number of flags).
 */
final class OutputValidator {

  static final int MAX_SUMMARY = 4000;
  static final int MAX_FLAGS = 30;
  static final int MAX_FIELD = 2000;

  private OutputValidator() {}

  static AnalysisOutput check(AnalysisOutput output) {
    if (output == null || isBlank(output.summary())) {
      throw new InvalidOutputException("empty summary");
    }
    if (output.summary().length() > MAX_SUMMARY) {
      throw new InvalidOutputException("summary too long");
    }
    List<ClauseFlag> flags = output.flags() == null ? List.of() : output.flags();
    if (flags.size() > MAX_FLAGS) {
      throw new InvalidOutputException("too many flags");
    }
    for (ClauseFlag flag : flags) {
      if (flag == null || flag.riskLevel() == null) {
        throw new InvalidOutputException("flag without risk level");
      }
      if (isBlank(flag.clauseText()) || isBlank(flag.explanation())) {
        throw new InvalidOutputException("flag without clause or explanation");
      }
      if (tooLong(flag.clauseText())
          || tooLong(flag.explanation())
          || tooLong(flag.suggestedCorrection())) {
        throw new InvalidOutputException("flag field too long");
      }
    }
    return new AnalysisOutput(output.summary(), List.copyOf(flags));
  }

  private static boolean tooLong(String value) {
    return value != null && value.length() > MAX_FIELD;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
