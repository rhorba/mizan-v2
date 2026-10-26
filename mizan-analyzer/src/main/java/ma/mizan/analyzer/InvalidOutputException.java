package ma.mizan.analyzer;

/**
 * The request or the model's answer cannot give a usable analysis. Not worth a Kafka retry. Carries
 * the tokens already billed for the attempts, so the failure still counts in the spend budget.
 */
class InvalidOutputException extends RuntimeException {

  private final int inputTokens;
  private final int outputTokens;

  InvalidOutputException(String reason) {
    this(reason, 0, 0);
  }

  InvalidOutputException(String reason, int inputTokens, int outputTokens) {
    super(reason);
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
  }

  int inputTokens() {
    return inputTokens;
  }

  int outputTokens() {
    return outputTokens;
  }
}
