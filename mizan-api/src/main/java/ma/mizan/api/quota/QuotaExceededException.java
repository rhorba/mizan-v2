package ma.mizan.api.quota;

/** A new analysis would go over a daily limit (Story 5.1, spend budget). */
public class QuotaExceededException extends RuntimeException {

  /** Which limit was reached. */
  public enum Reason {
    USER_CAP,
    GLOBAL_CAP,
    BUDGET
  }

  private final Reason reason;

  QuotaExceededException(Reason reason) {
    super("daily limit reached: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
