package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka wire contract (architecture §5): each app's message records must generate exactly the
 * schema committed in docs/schemas/kafka. Same helper in mizan-api and mizan-analyzer; after an
 * intended change, rewrite the file with {@code -Dschemas.write=true} and update the other app.
 */
final class WireSchemas {

  static final Path DIR = Path.of("..", "docs", "schemas", "kafka");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final SchemaGenerator GENERATOR =
      new SchemaGenerator(
          new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
              .with(
                  Option.INLINE_ALL_SCHEMAS,
                  Option.ADDITIONAL_FIXED_TYPES,
                  Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
              .build());

  private WireSchemas() {}

  static void assertMatches(String file, Class<?> message) throws IOException {
    JsonNode generated = GENERATOR.generateSchema(message);
    Path path = DIR.resolve(file);
    if (Boolean.getBoolean("schemas.write")) {
      Files.writeString(
          path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(generated) + "\n");
    }
    assertThat(path).as("committed schema %s", path).exists();
    assertThat(generated)
        .as(
            "%s no longer matches %s; if intended, rerun with -Dschemas.write=true and update the"
                + " other app",
            message.getSimpleName(), file)
        .isEqualTo(JSON.readTree(Files.readString(path)));
  }
}
