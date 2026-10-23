package ma.mizan.api.contracts;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal clamd INSTREAM client: the bytes go over the socket in chunks and are never written to
 * disk by us. Protocol: {@code zINSTREAM\0}, then [4-byte big-endian length][data]…, then a zero
 * length; the reply is {@code stream: OK\0} or {@code stream: <signature> FOUND\0}.
 */
class ClamdClient {

  private static final int CHUNK = 64 * 1024;
  private static final int MAX_REPLY = 4096;

  sealed interface Verdict {}

  record Clean() implements Verdict {}

  record Infected(String signature) implements Verdict {}

  private final AntivirusProperties config;

  ClamdClient(AntivirusProperties config) {
    this.config = config;
  }

  Verdict scan(byte[] bytes) throws IOException {
    // clamd speaks plain TCP only (no TLS); it is reachable only inside the compose network /
    // cluster (NetworkPolicy), never from outside.
    // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress(config.host(), config.port()),
          (int) config.connectTimeout().toMillis());
      socket.setSoTimeout((int) config.readTimeout().toMillis());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
      for (int offset = 0; offset < bytes.length; offset += CHUNK) {
        int length = Math.min(CHUNK, bytes.length - offset);
        out.writeInt(length);
        out.write(bytes, offset, length);
      }
      out.writeInt(0);
      out.flush();
      return parse(readReply(socket.getInputStream()));
    }
  }

  static Verdict parse(String reply) throws IOException {
    String body = reply.startsWith("stream: ") ? reply.substring("stream: ".length()) : reply;
    if (body.equals("OK")) {
      return new Clean();
    }
    if (body.endsWith(" FOUND")) {
      return new Infected(body.substring(0, body.length() - " FOUND".length()));
    }
    // "INSTREAM size limit exceeded. ERROR", unknown command, etc.
    throw new IOException("Unexpected clamd reply: " + body);
  }

  private static String readReply(InputStream in) throws IOException {
    ByteArrayOutputStream reply = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1 && b != 0) {
      if (reply.size() >= MAX_REPLY) {
        throw new IOException("clamd reply too long");
      }
      reply.write(b);
    }
    return reply.toString(StandardCharsets.US_ASCII).strip();
  }
}
