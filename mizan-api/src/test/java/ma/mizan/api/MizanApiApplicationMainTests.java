package ma.mizan.api;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MizanApiApplicationMainTests {

  @Test
  void mainStartsTheApplication() {
    assertThatCode(
            () ->
                MizanApiApplication.main(
                    new String[] {"--server.port=0", "--spring.jmx.enabled=false"}))
        .doesNotThrowAnyException();
  }
}
