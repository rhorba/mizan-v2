package ma.mizan.analyzer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Stub model (Story 3.2 technical note): plays scripted answers and records every prompt. */
class ScriptedChatModel implements ChatModel {

  static final String MODEL = "claude-opus-5-5";
  static final String VALID_JSON =
      """
      {"summary":"Contrat de prestation entre A et B.",
       "flags":[{"clauseText":"Pénalité de 10 % par jour de retard.","riskLevel":"HIGH",
                 "explanation":"Pénalité sans plafond.","suggestedCorrection":"Plafonner à 10 %."}]}
      """;

  private final Deque<Function<Prompt, ChatResponse>> script = new ArrayDeque<>();
  private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
  private Function<Prompt, ChatResponse> fallback = p -> answer(VALID_JSON);

  static ChatResponse answer(String text) {
    return answer(text, "end_turn", 1000, 200);
  }

  static ChatResponse answer(String text, String finishReason, int input, int output) {
    return new ChatResponse(
        List.of(
            new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finishReason).build())),
        ChatResponseMetadata.builder().model(MODEL).usage(new DefaultUsage(input, output)).build());
  }

  synchronized ScriptedChatModel then(Function<Prompt, ChatResponse> step) {
    script.addLast(step);
    return this;
  }

  synchronized ScriptedChatModel thenAnswer(String text) {
    return then(p -> answer(text));
  }

  synchronized ScriptedChatModel otherwise(Function<Prompt, ChatResponse> step) {
    fallback = step;
    return this;
  }

  synchronized void reset() {
    script.clear();
    prompts.clear();
    fallback = p -> answer(VALID_JSON);
  }

  List<Prompt> prompts() {
    return List.copyOf(prompts);
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    Function<Prompt, ChatResponse> step;
    synchronized (this) {
      prompts.add(prompt);
      step = script.isEmpty() ? fallback : script.pollFirst();
    }
    return step.apply(prompt);
  }
}
