package ma.mizan.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/**
 * ADR-6 prompt-injection guard: the contract goes in a delimited block the system prompt declares
 * to be data, and any closing delimiter inside the text is defused. Built with plain string
 * operations, never a template engine, so braces in a contract cannot break rendering.
 */
final class Prompts {

  static final Map<String, String> OUTPUT_LANGUAGES =
      Map.of(
          "fr", "French",
          "ar", "Modern Standard Arabic",
          "ary", "Moroccan Darija written in Arabic script");

  private static final Pattern CLOSING_TAG = Pattern.compile("(?i)</(\\s*contract)");
  private static final String SYSTEM_TEMPLATE = load("prompts/system.txt");

  private Prompts() {}

  static boolean supports(String language) {
    return language != null && OUTPUT_LANGUAGES.containsKey(language);
  }

  static String system(String language) {
    String outputLanguage = OUTPUT_LANGUAGES.get(language);
    if (outputLanguage == null) {
      throw new InvalidOutputException("unsupported language");
    }
    return SYSTEM_TEMPLATE.replace("{{OUTPUT_LANGUAGE}}", outputLanguage);
  }

  static String user(String contractText) {
    String defused =
        CLOSING_TAG
            .matcher(contractText)
            .replaceAll(m -> Matcher.quoteReplacement("<\\/" + m.group(1)));
    return "<contract>\n" + defused + "\n</contract>";
  }

  private static String load(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
