package ma.mizan.analyzer;

/** The request or the model's answer cannot give a usable analysis. Not worth a Kafka retry. */
class InvalidOutputException extends RuntimeException {

  InvalidOutputException(String reason) {
    super(reason);
  }
}
