package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MizanAnalyzerApplicationMainTests {

  @Test
  void mainStartsTheApplication() {
    assertThatCode(
            () ->
                MizanAnalyzerApplication.main(
                    new String[] {
                      "--spring.ai.anthropic.api-key=test-key-not-used",
                      "--spring.jmx.enabled=false",
                      "--spring.kafka.listener.auto-startup=false"
                    }))
        .doesNotThrowAnyException();
  }
}
