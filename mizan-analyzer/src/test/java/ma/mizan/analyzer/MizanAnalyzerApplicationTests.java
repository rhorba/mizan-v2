package ma.mizan.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MizanAnalyzerApplicationTests {

  @Autowired ApplicationContext context;

  @Test
  void contextLoadsWithKafkaAndChatModel() {
    assertThat(context.getBeanNamesForType(KafkaTemplate.class)).isNotEmpty();
    assertThat(context.getBeanNamesForType(ChatModel.class)).isNotEmpty();
  }
}
