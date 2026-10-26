package ma.mizan.analyzer;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Story 3.2, ADR-6: one model call per attempt with the language's system prompt and the API's
 * structured output; an unusable answer is retried once, then reported as invalid.
 */
@Service
class ContractAnalyzer {

  private static final Logger LOG = LoggerFactory.getLogger(ContractAnalyzer.class);

  /** "Invalid output twice" (Story 3.2) ends the analysis. */
  static final int ATTEMPTS = 2;

  private static final BeanOutputConverter<AnalysisOutput> CONVERTER =
      new BeanOutputConverter<>(AnalysisOutput.class);

  private final ChatModel chatModel;
  private final AnthropicChatOptions options;

  ContractAnalyzer(
      ChatModel chatModel,
      @Value("${mizan.analyzer.model}") String model,
      @Value("${mizan.analyzer.max-tokens}") int maxTokens) {
    this.chatModel = chatModel;
    this.options = options(model, maxTokens);
  }

  Result analyze(AnalysisRequest request) {
    if (!Prompts.supports(request.language())) {
      throw new InvalidOutputException("unsupported language");
    }
    if (request.text() == null || request.text().isBlank()) {
      throw new InvalidOutputException("empty contract text");
    }
    Prompt prompt =
        new Prompt(
            List.of(
                new SystemMessage(Prompts.system(request.language())),
                new UserMessage(Prompts.user(request.text()))),
            options);
    int inputTokens = 0;
    int outputTokens = 0;
    InvalidOutputException last = null;
    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      ChatResponse response = chatModel.call(prompt);
      // Wasted attempts are still billed: count them so the budget (Story 3.2/4.1) sees them.
      Usage usage = response.getMetadata().getUsage();
      inputTokens += inputTokens(usage);
      outputTokens += tokens(usage.getCompletionTokens());
      try {
        AnalysisOutput output = OutputValidator.check(parse(response));
        LOG.debug(
            "analysis={} cacheRead={} cacheWrite={}",
            request.analysisId(),
            usage.getCacheReadInputTokens(),
            usage.getCacheWriteInputTokens());
        return new Result(output, response.getMetadata().getModel(), inputTokens, outputTokens);
      } catch (InvalidOutputException e) {
        LOG.warn(
            "Unusable model answer for analysis={} attempt={}: {}",
            request.analysisId(),
            attempt,
            e.getMessage());
        last = e;
      }
    }
    throw new InvalidOutputException(last.getMessage(), inputTokens, outputTokens);
  }

  /**
   * Model and max tokens are set on every request: per-call options do not inherit the spring.ai
   * defaults, and a live run silently fell back to Spring AI's default model.
   */
  static AnthropicChatOptions options(String model, int maxTokens) {
    // No temperature/top_p/top_k: the API rejects them (ADR-6 amendment, 2026-10-26).
    return AnthropicChatOptions.builder()
        .model(model)
        .maxTokens(maxTokens)
        .outputSchema(CONVERTER.getJsonSchema())
        // The system prompt (~600 tokens) is the same for every contract in a language and for
        // the retry of an unusable answer; Opus 5.5 caches prefixes from 512 tokens (5 min TTL).
        .cacheOptions(
            AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
        .build();
  }

  /**
   * Input billed for one call. The API reports cache writes and reads apart from input_tokens; all
   * three count (writes cost 1.25x, reads 0.05x: counting both at 1x keeps the budget simple and
   * close).
   */
  static int inputTokens(Usage usage) {
    long cached =
        longValue(usage.getCacheReadInputTokens()) + longValue(usage.getCacheWriteInputTokens());
    return Math.toIntExact(tokens(usage.getPromptTokens()) + cached);
  }

  private static long longValue(Long value) {
    return value == null ? 0 : value;
  }

  private static AnalysisOutput parse(ChatResponse response) {
    Generation generation = response.getResult();
    if (generation == null || generation.getOutput() == null) {
      throw new InvalidOutputException("no answer");
    }
    String finishReason = generation.getMetadata().getFinishReason();
    if (finishReason != null && !"end_turn".equalsIgnoreCase(finishReason)) {
      // max_tokens (truncated JSON) or refusal.
      throw new InvalidOutputException("stopped: " + finishReason);
    }
    String text = generation.getOutput().getText();
    if (text == null || text.isBlank()) {
      throw new InvalidOutputException("empty answer");
    }
    try {
      return CONVERTER.convert(text);
    } catch (RuntimeException e) {
      throw new InvalidOutputException("answer is not the expected JSON");
    }
  }

  private static int tokens(Integer value) {
    return value == null ? 0 : value;
  }

  record Result(AnalysisOutput output, String model, int inputTokens, int outputTokens) {}
}
