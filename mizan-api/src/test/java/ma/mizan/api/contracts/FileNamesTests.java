package ma.mizan.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNamesTests {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "bail_commercial.pdf | bail_commercial.pdf",
        "C:\\Users\\karim\\Contrat.pdf | Contrat.pdf",
        "../../etc/passwd | passwd",
        "عقد كراء.pdf | عقد كراء.pdf",
        "  spaced.pdf   | spaced.pdf",
      })
  void keepsTheNameAndDropsPaths(String original, String expected) {
    assertThat(FileNames.sanitize(original)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "invoice\u202Efdp.exe", // right-to-left override: displays as "invoiceexe.pdf"
        "con\u200Btract.pdf", // zero-width space
        "con\u2066tract.pdf", // left-to-right isolate
        "\uFEFFcontract.pdf", // byte-order mark
        "con\u0000tract.pdf", // NUL
        "con\ntract.pdf", // control character
      })
  void removesInvisibleAndControlCharacters(String original) {
    String clean = FileNames.sanitize(original);
    assertThat(clean.codePoints())
        .noneMatch(c -> Character.getType(c) == Character.FORMAT || Character.isISOControl(c));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", ".", "..", "dir/", "\u202E"})
  void fallsBackWhenNothingUsableIsLeft(String original) {
    assertThat(FileNames.sanitize(original)).isEqualTo(FileNames.FALLBACK);
  }

  @org.junit.jupiter.api.Test
  void normalisesAndTruncatesToTwoHundredFiftyFiveCodePoints() {
    String decomposed = "e\u0301".repeat(300); // 300 × "é" written as e + combining accent
    String clean = FileNames.sanitize(decomposed);
    assertThat(clean.codePointCount(0, clean.length())).isEqualTo(255);
    assertThat(clean).isEqualTo("\u00E9".repeat(255));
  }
}
