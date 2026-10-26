package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.prompt.Prompt;

/** Story 3.2 at unit level: one call, structured output, "invalid output twice" ends it. */
class ContractAnalyzerTests {

  private final ScriptedChatModel model = new ScriptedChatModel();
  private final ContractAnalyzer analyzer = new ContractAnalyzer(model, "claude-opus-5-5", 16000);

  static AnalysisRequest request(String language, String text) {
    return new AnalysisRequest(
        1, UUID.randomUUID(), UUID.randomUUID(), language, text, Instant.now());
  }

  @Test
  void validAnswerIsMappedWithModelAndTokensInOneCall() {
    ContractAnalyzer.Result result = analyzer.analyze(request("fr", "Article 1. Pénalités."));

    assertThat(result.output().summary()).isEqualTo("Contrat de prestation entre A et B.");
    assertThat(result.output().flags())
        .singleElement()
        .extracting(ClauseFlag::riskLevel)
        .isEqualTo(RiskLevel.HIGH);
    assertThat(result.model()).isEqualTo(ScriptedChatModel.MODEL);
    assertThat(result.inputTokens()).isEqualTo(1000);
    assertThat(result.outputTokens()).isEqualTo(200);

    assertThat(model.prompts()).hasSize(1);
    Prompt prompt = model.prompts().getFirst();
    assertThat(prompt.getOptions().getModel()).isEqualTo("claude-opus-5-5");
    assertThat(prompt.getInstructions())
        .extracting(m -> m.getMessageType())
        .containsExactly(MessageType.SYSTEM, MessageType.USER);
    assertThat(prompt.getInstructions().get(1).getText())
        .isEqualTo("<contract>\nArticle 1. Pénalités.\n</contract>");
  }

  @Test
  void requestOptionsUseStructuredOutputAndNoSamplingParameters() {
    AnthropicChatOptions options = ContractAnalyzer.options("claude-opus-5-5", 16000);

    assertThat(options.getModel()).isEqualTo("claude-opus-5-5");
    assertThat(options.getMaxTokens()).isEqualTo(16000);
    assertThat(options.getOutputSchema()).contains("\"summary\"", "\"flags\"", "\"HIGH\"");
    // Rejected by the API since 2026-10-21 (ADR-6 amendment).
    assertThat(options.getTemperature()).isNull();
    assertThat(options.getTopP()).isNull();
    assertThat(options.getTopK()).isNull();
    assertThat(options.getCacheOptions().getStrategy())
        .isEqualTo(AnthropicCacheStrategy.SYSTEM_ONLY);
  }

  @Test
  void cachedInputTokensCountTowardsInput() {
    assertThat(ContractAnalyzer.inputTokens(new DefaultUsage(100, 50, 150, null, 600L, 0L)))
        .isEqualTo(700);
    assertThat(ContractAnalyzer.inputTokens(new DefaultUsage(100, 50, 150, null, null, 600L)))
        .isEqualTo(700);
    assertThat(ContractAnalyzer.inputTokens(new DefaultUsage(null, 50))).isZero();
  }

  @Test
  void oneInvalidAnswerIsRetriedAndBothAttemptsAreCounted() {
    model.thenAnswer("not json").thenAnswer(ScriptedChatModel.VALID_JSON);

    ContractAnalyzer.Result result = analyzer.analyze(request("ar", "Contrat"));

    assertThat(model.prompts()).hasSize(2);
    assertThat(result.inputTokens()).isEqualTo(2000);
    assertThat(result.outputTokens()).isEqualTo(400);
  }

  @Test
  void invalidOutputTwiceFails() {
    model
        .then(p -> ScriptedChatModel.answer("{\"summary\":\"cut", "max_tokens", 1000, 16000))
        .thenAnswer("{\"summary\":\"\",\"flags\":[]}");

    assertThatThrownBy(() -> analyzer.analyze(request("ary", "Contrat")))
        .isInstanceOf(InvalidOutputException.class)
        .hasMessage("empty summary");
    assertThat(model.prompts()).hasSize(ContractAnalyzer.ATTEMPTS);
  }

  @Test
  void refusalAndEmptyAnswersAreInvalid() {
    model
        .then(p -> ScriptedChatModel.answer("", "refusal", 10, 0))
        .then(p -> ScriptedChatModel.answer(" ", "end_turn", 10, 0));

    assertThatThrownBy(() -> analyzer.analyze(request("fr", "Contrat")))
        .isInstanceOf(InvalidOutputException.class)
        .hasMessage("empty answer");
  }

  @Test
  void unsupportedLanguageOrEmptyTextNeverCallsTheModel() {
    assertThatThrownBy(() -> analyzer.analyze(request("en", "Contract")))
        .isInstanceOf(InvalidOutputException.class);
    assertThatThrownBy(() -> analyzer.analyze(request("fr", "  ")))
        .isInstanceOf(InvalidOutputException.class);
    assertThatThrownBy(() -> analyzer.analyze(request("fr", null)))
        .isInstanceOf(InvalidOutputException.class);
    assertThat(model.prompts()).isEmpty();
  }
}
