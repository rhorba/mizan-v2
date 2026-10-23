package ma.mizan.api.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Optional;

/** Analysis language (FR-4): French, Modern Standard Arabic, Moroccan Darija. */
public enum Language {
  FR("fr"),
  AR("ar"),
  ARY("ary");

  private final String code;

  Language(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public static Optional<Language> fromCode(String code) {
    return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst();
  }

  /** Stored as the lowercase code the CHECK constraint expects. */
  @Converter(autoApply = true)
  static class JpaConverter implements AttributeConverter<Language, String> {
    @Override
    public String convertToDatabaseColumn(Language language) {
      return language == null ? null : language.code;
    }

    @Override
    public Language convertToEntityAttribute(String code) {
      return code == null ? null : fromCode(code).orElseThrow();
    }
  }
}
