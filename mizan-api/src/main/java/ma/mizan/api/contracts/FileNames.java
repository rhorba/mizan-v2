package ma.mizan.api.contracts;

import java.text.Normalizer;

/**
 * Makes a client-supplied file name safe to store and display: no path, no control characters, no
 * bidi overrides that could disguise "gpj.exe" as "exe.jpg", no zero-width characters. Arabic and
 * other letters are kept.
 */
final class FileNames {

  static final String FALLBACK = "contract.pdf";
  private static final int MAX_LENGTH = 255;

  private FileNames() {}

  static String sanitize(String original) {
    if (original == null) {
      return FALLBACK;
    }
    String name =
        original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
    name = Normalizer.normalize(name, Normalizer.Form.NFC);
    StringBuilder clean = new StringBuilder();
    name.codePoints().filter(c -> !isUnsafe(c)).forEach(clean::appendCodePoint);
    String result = clean.toString().strip();
    if (result.isEmpty() || result.equals(".") || result.equals("..")) {
      return FALLBACK;
    }
    if (result.codePointCount(0, result.length()) > MAX_LENGTH) {
      result = result.substring(0, result.offsetByCodePoints(0, MAX_LENGTH));
    }
    return result;
  }

  private static boolean isUnsafe(int c) {
    int type = Character.getType(c);
    return type == Character.CONTROL
        || type == Character.FORMAT // bidi controls (U+202A–202E, U+2066–2069), ZWSP family, BOM
        || type == Character.UNASSIGNED
        || type == Character.PRIVATE_USE
        || type == Character.SURROGATE;
  }
}
