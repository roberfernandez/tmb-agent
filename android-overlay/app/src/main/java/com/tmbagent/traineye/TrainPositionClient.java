package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The live map and L4 station boards use the same Engine.IO session. */
final class TrainPositionClient implements AutoCloseable {
    private static final URI ORIGIN = URI.create("https://miralin.cat");
    private static final long STALE_MAP_MS = 90_000;
    private static final long BOARD_REFRESH_MS = 40_000;
    private static final long BOARD_FRESH_MS = 60_000;
    private static final long BOARD_RETRY_MS = 20_000;
    private static final long BOARD_RECOVERY_MS = 60_000;
    private static final long POST_RETRY_MS = 5_000;

    interface Listener {
        void onMap(JSONObject trains);
        void onState(String status);
        default void onBoard(String stop, JSONObject board) {}
        default void onBoardState(String stop, String status, boolean error) {}
    }

    interface Transport {
        String request(String session, String body) throws IOException;
        void cancel();
    }

    private final Listener listener;
    private final SocketIoConnection standard;
    private final SocketDiagnostics diagnostics = new SocketDiagnostics();
    private final Transport reader;
    private final Supplier<Transport> writerTransport;
    private final LongSupplier elapsedMs;
    private volatile boolean closed;
    private volatile SessionWriter activeWriter;
    private volatile Set<String> stops = Collections.emptySet();
    private Thread worker;
    private boolean sessionHadMap;

    TrainPositionClient(Listener listener) {
        this(listener, ORIGIN, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    TrainPositionClient(Listener listener, URI origin, LongSupplier elapsedMs) {
        this.listener = listener;
        this.reader = null;
        this.writerTransport = null;
        this.elapsedMs = elapsedMs;
        this.standard = new SocketIoConnection(listener, origin, elapsedMs);
    }

    // Kept for regression testing the legacy polling transport.
    TrainPositionClient(Listener listener, String endpoint, LongSupplier elapsedMs) {
        this(listener, new PollingTransport(endpoint), () -> new PollingTransport(endpoint), elapsedMs);
    }

    TrainPositionClient(Listener listener, Transport reader, Supplier<Transport> writerTransport, LongSupplier elapsedMs) {
        this.listener = listener;
        this.reader = reader;
        this.writerTransport = writerTransport;
        this.elapsedMs = elapsedMs;
        this.standard = null;
    }

    void setStops(Set<String> requested) {
        if (stops.equals(requested)) return;
        Set<String> valid = new LinkedHashSet<>();
        for (String stop : requested) if (stop.matches("41[3-9]|42[0-9]|43[0-4]")) valid.add(stop);
        stops = valid;
        if (standard != null) { standard.setStops(valid); return; }
        SessionWriter writer = activeWriter;
        if (writer != null) writer.syncSubscriptions();
    }

    synchronized void start() {
        if (closed) return;
        if (standard != null) { standard.start(); return; }
        if (worker != null) return;
        worker = new Thread(this::run, "miralin-direct");
        worker.start();
    }

    private void run() {
        long retry = 2000;
        while (!closed) {
            try {
                sessionHadMap = false;
                listener.onState("Miralin directe · connectant…");
                receiveSession();
            } catch (IOException error) {
                if (!closed) {
                    listener.onBoardState("", error.getMessage(), true);
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    listener.onState("Miralin directe · " + cause.getClass().getSimpleName());
                }
            }
            if (sessionHadMap) retry = 2000;
            if (!closed) {
                try { Thread.sleep(retry + ThreadLocalRandom.current().nextInt(1000)); }
                catch (InterruptedException ignored) { if (closed) break; }
                retry = Math.min(retry * 2, 30000);
            }
        }
    }

    void receiveSession() throws IOException {
        listener.onBoardState("", "Handshake (GET)", false);
        String hello = request(reader, "Handshake (GET)", null, null);
        String sid;
        try {
            sid = hello.startsWith("0") ? new JSONObject(hello.substring(1)).optString("sid", "") : "";
        } catch (JSONException error) {
            throw new IOException("Handshake (GET) · respuesta inválida", error);
        }
        if (sid.isEmpty()) throw new IOException("Handshake (GET) · sin sid");

        diagnostics.session(elapsedMs.getAsLong());
        SessionWriter writer = new SessionWriter(sid, writerTransport.get());
        activeWriter = writer;
        try {
            if (closed) return;
            listener.onBoardState("", "Apertura del canal (POST 40)", false);
            request(reader, "Apertura del canal (POST 40)", sid, "40");
            listener.onBoardState("", "Confirmación del canal (GET 40)", false);
            long lastMap = elapsedMs.getAsLong();
            long openedAt = lastMap;
            while (!closed) {
                if (writer.heartbeatError != null) throw writer.heartbeatError;
                String phase = writer.namespaceReady ? "Espera de eventos (GET)" : "Confirmación del canal (GET 40)";
                String payload = request(reader, phase, sid, null);
                for (String packet : payload.split("\u001e")) {
                    if (closed) return;
                    if (packet.equals("2")) {
                        writer.pong();
                    } else if (packet.equals("1") || packet.startsWith("41")) {
                        throw new IOException(phase + " · sesión cerrada");
                    } else if (packet.startsWith("44")) {
                        throw new IOException("Confirmación del canal (GET 40) · conexión rechazada");
                    } else if (packet.startsWith("40")) {
                        writer.namespaceReady = true;
                        listener.onBoardState("", "Canal confirmado", false);
                    } else if (packet.startsWith("42")) {
                        JSONArray event;
                        try { event = new JSONArray(packet.substring(2)); }
                        catch (JSONException error) {
                            listener.onBoardState("", "Espera de eventos (GET) · evento inválido", true);
                            continue;
                        }
                        diagnostics.event(event.optString(0));
                        JSONObject data = event.optJSONObject(1);
                        if ("stopDataUpdate".equals(event.optString(0))) {
                            if (data != null) writer.acceptBoard(data);
                            else {
                                JSONObject frame = new JSONObject();
                                try { frame.put("payload", event.opt(1)); } catch (JSONException ignored) {}
                                diagnostics.board("", frame, "payload is not an object", elapsedMs.getAsLong());
                            }
                        }
                        if (!"mapUpdate".equals(event.optString(0))) continue;
                        JSONObject line = data == null ? null : data.optJSONObject("L4");
                        JSONObject trains = line == null ? null : line.optJSONObject("trains");
                        if (trains != null) {
                            sessionHadMap = true;
                            lastMap = elapsedMs.getAsLong();
                            listener.onMap(TrainHeadway.positionsFromLine(line));
                            listener.onState("Miralin directe · dades rebudes");
                        }
                    }
                }
                writer.syncSubscriptions();
                long now = elapsedMs.getAsLong();
                if (!writer.namespaceReady && now - openedAt > STALE_MAP_MS) {
                    throw new IOException("Confirmación del canal (GET 40) · sin respuesta");
                }
                if (now - lastMap > STALE_MAP_MS) throw new IOException("Espera de eventos (GET) · sin mapa reciente");
                // A healthy position stream does not prove that station subscriptions work.
                // Recreate the sid if re-subscribing cannot restore a requested board.
                writer.checkBoards(now);
            }
        } finally {
            activeWriter = null;
            writer.stop();
            if (!closed && writer.awaitStop()) {
                try { reader.request(sid, "1"); } catch (IOException ignored) {}
            }
        }
    }

    JSONObject diagnosticSnapshot() {
        if (standard != null) return standard.diagnosticSnapshot();
        JSONObject report = diagnostics.snapshot(elapsedMs.getAsLong());
        try {
            JSONArray requested = new JSONArray();
            for (String stop : stops) requested.put(stop);
            SessionWriter writer = activeWriter;
            report.put("requestedStops", requested).put("closed", closed)
                .put("namespaceReady", writer != null && writer.namespaceReady);
        } catch (JSONException ignored) {}
        return report;
    }

    private String request(Transport transport, String phase, String sid, String body) throws IOException {
        diagnostics.requestStarted(phase, body, elapsedMs.getAsLong());
        try {
            String response;
            try {
                response = transport.request(sid, body);
            } catch (IOException error) {
                String detail = error.getMessage();
                if (detail == null || !detail.startsWith("HTTP ")) detail = SocketDiagnostics.errorText(error);
                throw new IOException(phase + " · " + detail, error);
            }
            if (body != null && !"ok".equals(response)) throw new IOException(phase + " · respuesta distinta de ok");
            diagnostics.requestFinished(phase, body, "", elapsedMs.getAsLong());
            return response;
        } catch (IOException error) {
            diagnostics.requestFinished(phase, body, error.getMessage(), elapsedMs.getAsLong());
            throw error;
        }
    }

    @Override public synchronized void close() {
        closed = true;
        if (standard != null) { standard.close(); return; }
        reader.cancel();
        SessionWriter writer = activeWriter;
        if (writer != null) writer.stop();
        if (worker != null) worker.interrupt();
    }

    /** Serializes every POST after the handshake while the reader keeps polling maps. */
    private final class SessionWriter {
        private final String sid;
        private final Transport transport;
        private final ExecutorService posts = Executors.newSingleThreadExecutor(r -> new Thread(r, "miralin-post"));
        private final Set<String> attempted = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> retryAt = new ConcurrentHashMap<>();
        private final Map<String, Long> lastBoard = new ConcurrentHashMap<>();
        private final Map<String, Long> firstRequest = new ConcurrentHashMap<>();
        private final AtomicBoolean subscriptionQueued = new AtomicBoolean();
        private volatile String onlyAttempted;
        private volatile boolean namespaceReady;
        private volatile boolean stopped;
        private volatile IOException heartbeatError;

        SessionWriter(String sid, Transport transport) {
            this.sid = sid;
            this.transport = transport;
        }

        void pong() {
            enqueue(() -> {
                try { request(transport, "Latido (POST pong)", sid, "3"); }
                catch (IOException error) {
                    heartbeatError = error;
                    if (!stopped) listener.onBoardState("", error.getMessage(), true);
                }
            });
        }

        void syncSubscriptions() {
            if (!namespaceReady || stopped || dueStop() == null || !subscriptionQueued.compareAndSet(false, true)) return;
            enqueue(() -> {
                try { subscribeNext(); }
                finally { subscriptionQueued.set(false); }
                // Queue the next stop behind any pending heartbeat.
                syncSubscriptions();
            });
        }

        private String dueStop() {
            long now = elapsedMs.getAsLong();
            for (String stop : stops) {
                Long last = lastBoard.get(stop);
                if ((last == null || now - last >= BOARD_REFRESH_MS)
                    && now >= retryAt.getOrDefault(stop, Long.MIN_VALUE)) return stop;
            }
            return null;
        }

        private void subscribeNext() {
            String stop = dueStop();
            if (stopped || stop == null) return;
            String phase = "Suscripción " + stop + " (POST)";
            Long last = lastBoard.get(stop);
            if (attempted.contains(stop) && (last == null || elapsedMs.getAsLong() - last >= BOARD_FRESH_MS)) {
                listener.onBoardState(stop, "Sin cuadro reciente · reintentando " + stop, true);
            }
            onlyAttempted = onlyAttempted == null || onlyAttempted.equals(stop) ? stop : "";
            attempted.add(stop);
            firstRequest.putIfAbsent(stop, elapsedMs.getAsLong());
            listener.onBoardState(stop, phase, false);
            String packet = "42[\"subscribeStop\",{\"line\":\"L4\",\"stop\":" + JSONObject.quote(stop) + "}]";
            try {
                request(transport, phase, sid, packet);
                retryAt.put(stop, elapsedMs.getAsLong() + BOARD_RETRY_MS);
                if (!stopped && lastBoard.get(stop) == last) {
                    listener.onBoardState(stop, "Esperando stopDataUpdate " + stop + " (GET)", false);
                }
            } catch (IOException error) {
                retryAt.put(stop, elapsedMs.getAsLong() + POST_RETRY_MS);
                if (!stopped) listener.onBoardState(stop, error.getMessage(), true);
            }
        }

        void checkBoards(long now) throws IOException {
            for (String stop : stops) {
                Long requested = firstRequest.get(stop);
                if (requested == null) continue;
                long last = lastBoard.getOrDefault(stop, requested);
                if (now - last >= BOARD_RECOVERY_MS) {
                    throw new IOException("Cuadro " + stop + " sin respuesta reciente · renovando sesión");
                }
            }
        }

        void acceptBoard(JSONObject board) {
            JSONObject wire = board;
            JSONObject data = board.optJSONObject("data");
            JSONObject rows = data == null ? board : data;
            if (rows.optJSONArray("via1") == null && rows.optJSONArray("via2") == null) {
                diagnostics.board("", wire, "missing via1/via2 arrays", elapsedMs.getAsLong());
                return;
            }
            String line = data == null ? board.optString("line", "L4") : data.optString("line", board.optString("line", "L4"));
            if (!"L4".equals(line)) {
                diagnostics.board("", wire, "line is " + line, elapsedMs.getAsLong());
                return;
            }
            String stop = ServiceAlerts.boardStop(board);
            // Stale subscriptions are filtered locally so a change of stop cannot reset the map.
            // An unlabelled board is only safe before any other stop has been requested on this sid.
            if (stop.isEmpty()) stop = onlyAttempted;
            if (stop == null || stop.isEmpty()) {
                diagnostics.board("", wire, "unlabelled board after multiple or no station requests", elapsedMs.getAsLong());
                return;
            }
            if (!stops.contains(stop) || !attempted.contains(stop)) {
                diagnostics.board(stop, wire, "station was not requested on this session", elapsedMs.getAsLong());
                return;
            }
            if (ServiceAlerts.boardStop(board).isEmpty()) {
                // Only this station has ever been requested on this sid. Carry its
                // verified scope into both ETA and headway consumers, which must
                // never infer a target from the current location in a train row.
                try { board = new JSONObject(board.toString()).put("stopCode", stop); }
                catch (JSONException ignored) {
                    diagnostics.board(stop, wire, "cannot attach station scope", elapsedMs.getAsLong());
                    return;
                }
            }
            long now = elapsedMs.getAsLong();
            diagnostics.board(stop, wire, "accepted", now);
            Long previous = lastBoard.put(stop, now);
            if (previous == null || now - previous >= BOARD_REFRESH_MS) {
                listener.onBoardState(stop, "stopDataUpdate " + stop + " recibido", false);
            }
            listener.onBoard(stop, board);
        }

        private void enqueue(Runnable action) {
            if (stopped) return;
            try { posts.execute(action); }
            catch (RejectedExecutionException ignored) { /* Session closed while queuing. */ }
        }

        void stop() {
            stopped = true;
            posts.shutdownNow();
            transport.cancel();
        }

        boolean awaitStop() {
            try { return posts.awaitTermination(2, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return false; }
        }
    }

    private static final class PollingTransport implements Transport {
        private final String endpoint;
        private volatile HttpURLConnection active;
        private volatile boolean cancelled;

        PollingTransport(String endpoint) { this.endpoint = endpoint; }

        @Override public String request(String session, String body) throws IOException {
            if (cancelled) throw new IOException("Stopped");
            String url = endpoint + (session == null ? "" : "&sid=" + URLEncoder.encode(session, "UTF-8"));
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            active = connection;
            try {
                if (cancelled) throw new IOException("Stopped");
                boolean closing = "1".equals(body);
                boolean subscribing = body != null && body.startsWith("42");
                connection.setConnectTimeout(closing ? 2000 : subscribing ? 4000 : 10000);
                connection.setReadTimeout(closing ? 2000 : subscribing ? 4000 : 40000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(body == null ? "GET" : "POST");
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
                    // Use buffered POSTs for station subscriptions, as in the Android probe.
                    if (!subscribing) connection.setFixedLengthStreamingMode(bytes.length);
                    try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (output.size() + size > 4 * 1024 * 1024) throw new IOException("Response too large");
                        output.write(buffer, 0, size);
                    }
                    return output.toString("UTF-8");
                }
            } finally {
                connection.disconnect();
                if (active == connection) active = null;
            }
        }

        @Override public void cancel() {
            cancelled = true;
            HttpURLConnection connection = active;
            if (connection != null) connection.disconnect();
        }
    }
}
