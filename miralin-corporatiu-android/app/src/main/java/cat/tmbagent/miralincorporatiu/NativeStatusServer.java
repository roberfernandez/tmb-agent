package cat.tmbagent.miralincorporatiu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Read-only status bridge for the user's PWA, bound exclusively to Android loopback.
 * No train data, credentials or write commands are exposed to remote networks.
 * This lets Chrome reconcile the eye's real state after the floating X stops it,
 * without opening the PWA or bringing it to the foreground.
 */
final class NativeStatusServer {
    static final int PORT = 18764;
    private static final String ALLOWED_ORIGIN = "https://roberfernandez.github.io";
    private final Supplier<String> status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ServerSocket server;

    NativeStatusServer(Supplier<String> status) { this.status = status; }

    private volatile boolean closed;

    void start() {
        // Binding and accepting sockets must never block the Android main thread.
        worker.execute(() -> {
            try (ServerSocket listening = new ServerSocket(PORT, 4,
                    InetAddress.getByName("127.0.0.1"))) {
                synchronized (this) {
                    if (closed) return;
                    server = listening;
                }
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    try (Socket client = listening.accept()) {
                        handle(client);
                    } catch (IOException problem) {
                        if (closed) return;
                    } catch (Exception ignored) {
                        // A malformed request must not stop native tracking.
                    }
                }
            } catch (IOException unavailable) {
                // The eye itself continues when loopback is unavailable.
            } finally {
                synchronized (this) { server = null; }
            }
        });
    }

    private void handle(Socket client) throws IOException {
        client.setSoTimeout(1500);
        BufferedReader input = new BufferedReader(
            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        String request = input.readLine();
        if (request == null || request.length() > 2048) return;
        boolean options = request.startsWith("OPTIONS /status HTTP/1.");
        boolean get = request.startsWith("GET /status HTTP/1.");
        String origin = null;
        int scanned = request.length();
        for (int i = 0; i < 32; i++) {
            String header = input.readLine();
            if (header == null || header.isEmpty()) break;
            scanned += header.length();
            if (scanned > 8192) return;
            if (header.regionMatches(true, 0, "Origin: ", 0, 8))
                origin = header.substring(8).trim();
        }
        // An untrusted website must not be able to read the state of the motor.
        if (!ALLOWED_ORIGIN.equals(origin)) {
            respond(client, 403, "{}", false);
            return;
        }
        if (options) {
            respond(client, 204, "", true);
        } else if (get) {
            respond(client, 200, status.get(), true);
        } else {
            respond(client, 405, "{}", true);
        }
    }

    private static void respond(Socket client, int code, String body, boolean allowed) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 204 ? "No Content"
            : code == 403 ? "Forbidden" : "Method Not Allowed";
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Content-Length: " + data.length + "\r\n"
            + "Cache-Control: no-store\r\n"
            + "Connection: close\r\n"
            + (allowed
                ? "Access-Control-Allow-Origin: " + ALLOWED_ORIGIN + "\r\n"
                + "Access-Control-Allow-Methods: GET, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type\r\n"
                + "Access-Control-Allow-Private-Network: true\r\n"
                + "Vary: Origin\r\n" : "")
            + "\r\n";
        OutputStream out = client.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.flush();
    }

    synchronized void close() {
        closed = true;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) {}
            server = null;
        }
        worker.shutdownNow();
    }
}
