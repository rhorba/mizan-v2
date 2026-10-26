package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OutputValidatorTests {

  static final ClauseFlag FLAG = new ClauseFlag("Clause", RiskLevel.HIGH, "Why", "");

  @Test
  void validOutputPassesAndMissingFlagsBecomeEmpty() {
    assertThat(OutputValidator.check(new AnalysisOutput("Résumé", List.of(FLAG))).flags())
        .containsExactly(FLAG);
    assertThat(OutputValidator.check(new AnalysisOutput("Résumé", null)).flags()).isEmpty();
  }

  @Test
  void suggestedCorrectionMayBeMissing() {
    ClauseFlag noCorrection = new ClauseFlag("Clause", RiskLevel.LOW, "Why", null);

    assertThat(OutputValidator.check(new AnalysisOutput("S", List.of(noCorrection))).flags())
        .containsExactly(noCorrection);
  }

  static Stream<AnalysisOutput> invalid() {
    String tooLong = "x".repeat(OutputValidator.MAX_FIELD + 1);
    List<ClauseFlag> withNull = new ArrayList<>();
    withNull.add(null);
    return Stream.of(
        null,
        new AnalysisOutput(" ", List.of()),
        new AnalysisOutput("x".repeat(OutputValidator.MAX_SUMMARY + 1), List.of()),
        new AnalysisOutput("S", Collections.nCopies(OutputValidator.MAX_FLAGS + 1, FLAG)),
        new AnalysisOutput("S", withNull),
        new AnalysisOutput("S", List.of(new ClauseFlag("C", null, "W", ""))),
        new AnalysisOutput("S", List.of(new ClauseFlag(" ", RiskLevel.LOW, "W", ""))),
        new AnalysisOutput("S", List.of(new ClauseFlag("C", RiskLevel.LOW, null, ""))),
        new AnalysisOutput("S", List.of(new ClauseFlag(tooLong, RiskLevel.LOW, "W", ""))),
        new AnalysisOutput("S", List.of(new ClauseFlag("C", RiskLevel.LOW, tooLong, ""))),
        new AnalysisOutput("S", List.of(new ClauseFlag("C", RiskLevel.LOW, "W", tooLong))));
  }

  @ParameterizedTest
  @MethodSource("invalid")
  void unusableOutputIsRejected(AnalysisOutput output) {
    assertThatThrownBy(() -> OutputValidator.check(output))
        .isInstanceOf(InvalidOutputException.class);
  }
}
