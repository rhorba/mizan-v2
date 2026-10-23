package ma.mizan.api.contracts;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for the history: the (created_at, id) of the last row of a page, so the next
 * page is an index range scan and stays stable while new contracts are added.
 */
record HistoryCursor(Instant createdAt, UUID id) {

  static HistoryCursor after(Contract last) {
    return new HistoryCursor(last.createdAt(), last.id());
  }

  String encode() {
    long micros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((micros + "|" + id).getBytes(StandardCharsets.US_ASCII));
  }

  /** Anything that is not a cursor this API produced is a 400. */
  static HistoryCursor decode(String token) {
    try {
      String[] parts =
          new String(Base64.getUrlDecoder().decode(token), StandardCharsets.US_ASCII).split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("malformed cursor");
      }
      Instant createdAt = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
      return new HistoryCursor(createdAt, UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw new ContractProblemException(ContractProblem.BAD_CURSOR);
    }
  }
}
