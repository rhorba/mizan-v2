package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the system prompt is cached, on Haiku 5.5 to keep it under a cent: same 512-token minimum
 * and caching rules as Opus 5.5. Excluded from normal builds like {@link LiveAnalysisTests}.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class PromptCacheLiveTests {

  private static final Logger LOG = LoggerFactory.getLogger(PromptCacheLiveTests.class);

  @Autowired ChatModel chatModel;

  @Test
  void secondCallWithTheSameLanguageReadsTheSystemPromptFromCache() {
    Prompt prompt =
        new Prompt(
            List.of(
                new SystemMessage(Prompts.system("fr")),
                new UserMessage(Prompts.user("Article 1. Paiement à 30 jours."))),
            ContractAnalyzer.options("claude-haiku-5-5", 2000));

    Usage first = chatModel.call(prompt).getMetadata().getUsage();
    Usage second = chatModel.call(prompt).getMetadata().getUsage();

    LOG.info(
        "CACHE first write={} read={} | second write={} read={}",
        first.getCacheWriteInputTokens(),
        first.getCacheReadInputTokens(),
        second.getCacheWriteInputTokens(),
        second.getCacheReadInputTokens());
    assertThat(second.getCacheReadInputTokens()).isNotNull().isPositive();
  }
}
