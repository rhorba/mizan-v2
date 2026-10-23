package ma.mizan.api.contracts;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A clamd stand-in that speaks the real INSTREAM protocol on a local port. It reports a fixed
 * signature when the stream contains {@link #MARKER}, so tests need no real virus signatures.
 */
final class FakeClamd implements AutoCloseable {

  static final String MARKER = "MIZAN-TEST-MALWARE-MARKER";
  static final String SIGNATURE = "Mizan.Test.Signature";

  private final ServerSocket server;
  private final AtomicInteger scans = new AtomicInteger();
  private volatile String forcedReply;

  FakeClamd() throws IOException {
    // Loopback test double for clamd (plain TCP like the real one).
    // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
    server = new ServerSocket(0);
    Thread.ofVirtual().start(this::serve);
  }

  int port() {
    return server.getLocalPort();
  }

  int scans() {
    return scans.get();
  }

  /** Make every reply this exact string (to simulate clamd errors). */
  void replyWith(String reply) {
    forcedReply = reply;
  }

  private void serve() {
    while (!server.isClosed()) {
      try (Socket client = server.accept()) {
        DataInputStream in = new DataInputStream(client.getInputStream());
        byte[] command = new byte["zINSTREAM\0".length()];
        in.readFully(command);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        int length;
        while ((length = in.readInt()) > 0) {
          byte[] chunk = new byte[length];
          in.readFully(chunk);
          received.write(chunk);
        }
        scans.incrementAndGet();
        String body = received.toString(StandardCharsets.ISO_8859_1);
        String reply =
            forcedReply != null
                ? forcedReply
                : body.contains(MARKER) ? "stream: " + SIGNATURE + " FOUND" : "stream: OK";
        OutputStream out = client.getOutputStream();
        out.write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
        out.flush();
      } catch (IOException e) {
        // client went away or server closed
      }
    }
  }

  @Override
  public void close() throws IOException {
    server.close();
  }
}
