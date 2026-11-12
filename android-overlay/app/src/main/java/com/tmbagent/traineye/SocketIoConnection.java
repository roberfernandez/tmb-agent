package com.tmbagent.traineye;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;
import io.socket.thread.EventThread;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Standard Socket.IO connection shared by map and selected-station forecasts. */
final class SocketIoConnection implements AutoCloseable {
    private final TrainPositionClient.Listener listener;
    private final URI origin;
    private final LongSupplier clock;
    private final long retryDelay;
    private final SocketDiagnostics trace = new SocketDiagnostics();
    private final OkHttpClient network = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "miralin-health"));
    private volatile Set<String> stops = Collections.emptySet();
    private volatile Socket socket;
    private volatile boolean closed, connected;
    private volatile String transportMode = "websocket", lastTransport = "";
    private boolean started;
    private int websocketErrors;
    private long lastMap;
    // Accessed only on Socket.IO's event thread.
    private final Set<String> attempted = new HashSet<>();
    private final Map<String, Long> firstRequest = new HashMap<>();
    private final Map<String, Long> lastRequest = new HashMap<>();
    private final Map<String, Long> lastBoard = new HashMap<>();

    SocketIoConnection(TrainPositionClient.Listener listener, URI origin, LongSupplier clock) {
        this(listener, origin, clock, 2000);
    }

    SocketIoConnection(TrainPositionClient.Listener listener, URI origin, LongSupplier clock, long retryDelay) {
        this.listener = listener;
        this.origin = origin;
        this.clock = clock;
        this.retryDelay = retryDelay;
    }

    synchronized void start() {
        if (started || closed) return;
        started = true;
        EventThread.exec(() -> open(true));
        watchdog.scheduleAtFixedRate(this::tick, 5, 5, TimeUnit.SECONDS);
    }

    void setStops(Set<String> requested) {
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String stop : requested) if (stop.matches("41[3-9]|42[0-9]|43[0-4]")) valid.add(stop);
        stops = Collections.unmodifiableSet(valid);
        EventThread.exec(() -> { if (!closed) syncSubscriptions(); });
    }

    private void open(boolean websocketFirst) {
        if (closed) return;
        connected = false;
        Socket old = socket;
        socket = null;
        dispose(old);
        attempted.clear();
        firstRequest.clear();
        lastRequest.clear();
        lastBoard.clear();
        transportMode = websocketFirst ? "websocket" : "polling+upgrade";
        IO.Options options = new IO.Options();
        options.forceNew = true;
        options.multiplex = false;
        options.transports = websocketFirst ? new String[]{WebSocket.NAME} : new String[]{Polling.NAME, WebSocket.NAME};
        options.reconnection = true;
        options.reconnectionDelay = retryDelay;
        options.reconnectionDelayMax = 30_000;
        options.randomizationFactor = 0.2;
        options.timeout = 12_000;
        options.callFactory = network;
        options.webSocketFactory = this::openWebSocket;
        final Socket current = IO.socket(origin, options);
        socket = current;
        listener.onState("Miralin directe · connectant Socket.IO…");
        trace.state("connect", transportMode, clock.getAsLong());
        current.io().on(Manager.EVENT_TRANSPORT, args -> {
            if (!current(current) || args.length == 0 || !(args[0] instanceof Transport)) return;
            lastTransport = ((Transport) args[0]).name;
            trace.state("transport", lastTransport, clock.getAsLong());
        });
        current.io().on(Manager.EVENT_OPEN, args -> {
            if (current(current)) trace.session(clock.getAsLong());
        });
        current.io().on(Manager.EVENT_RECONNECT_ATTEMPT, args -> {
            if (current(current)) trace.state("reconnect-attempt", transportMode, clock.getAsLong());
        });
        current.on(Socket.EVENT_CONNECT, args -> {
            if (!current(current)) return;
            connected = true;
            websocketErrors = 0;
            lastMap = clock.getAsLong();
            attempted.clear();
            firstRequest.clear();
            lastRequest.clear();
            lastBoard.clear();
            listener.onBoardState("", "Canal Socket.IO confirmado · " + transportMode, false);
            syncSubscriptions();
        });
        current.on(Socket.EVENT_DISCONNECT, args -> {
            if (!current(current)) return;
            connected = false;
            String reason = args.length == 0 ? "desconexión" : String.valueOf(args[0]);
            trace.state("disconnect", reason, clock.getAsLong());
            listener.onBoardState("", "Socket.IO · " + reason, true);
            listener.onState("Miralin directe · reconnectant…");
            // Socket.IO automatically reconnects transport failures, but not a
            // server-requested namespace disconnect.
            if ("io server disconnect".equals(reason) && !closed) {
                try {
                    watchdog.schedule(() -> EventThread.exec(() -> {
                        if (current(current) && !connected) current.connect();
                    }), retryDelay, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException ignored) { /* User stopped tracking. */ }
            }
        });
        current.on(Socket.EVENT_CONNECT_ERROR, args -> {
            if (!current(current)) return;
            connected = false;
            String detail = SocketDiagnostics.errorText(args.length == 0 ? null : args[0]);
            trace.state("connect-error", detail, clock.getAsLong());
            listener.onBoardState("", "Socket.IO " + transportMode + " · " + detail, true);
            listener.onState("Miralin directe · reconnectant…");
            if (websocketFirst && ++websocketErrors >= 3) {
                trace.state("fallback", "Standard Socket.IO polling with WebSocket upgrade", clock.getAsLong());
                open(false);
            }
        });
        current.onAnyIncoming(args -> {
            if (current(current) && args.length > 0) trace.event(String.valueOf(args[0]));
        });
        current.on("mapUpdate", args -> {
            if (!current(current)) return;
            JSONObject data = object(args);
            JSONObject line = data == null ? null : data.optJSONObject("L4");
            if (line == null || line.optJSONObject("trains") == null) return;
            lastMap = clock.getAsLong();
            listener.onMap(TrainHeadway.positionsFromLine(line));
            listener.onState("Miralin directe · dades rebudes · Socket.IO");
        });
        current.on("stopDataUpdate", args -> {
            if (!current(current)) return;
            JSONObject raw = object(args);
            if (raw == null) {
                JSONObject frame = new JSONObject();
                try { frame.put("payload", args.length == 0 ? JSONObject.NULL : args[0]); } catch (JSONException ignored) {}
                trace.board("", frame, "payload is not an object", clock.getAsLong());
            } else acceptBoard(raw);
        });
        current.connect();
    }

    private okhttp3.WebSocket openWebSocket(okhttp3.Request request, WebSocketListener delegate) {
        return network.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(okhttp3.WebSocket socket, Response response) { delegate.onOpen(socket, response); }
            @Override public void onMessage(okhttp3.WebSocket socket, String text) { delegate.onMessage(socket, text); }
            @Override public void onMessage(okhttp3.WebSocket socket, ByteString bytes) { delegate.onMessage(socket, bytes); }
            @Override public void onClosing(okhttp3.WebSocket socket, int code, String reason) {
                // Complete the WebSocket close handshake so the Engine.IO
                // listener receives onClosed promptly and can reconnect.
                delegate.onClosing(socket, code, reason);
                socket.close(code, reason);
            }
            @Override public void onClosed(okhttp3.WebSocket socket, int code, String reason) { delegate.onClosed(socket, code, reason); }
            @Override public void onFailure(okhttp3.WebSocket socket, Throwable error, Response response) { delegate.onFailure(socket, error, response); }
        });
    }

    private boolean current(Socket candidate) { return !closed && socket == candidate; }

    private static JSONObject object(Object[] args) {
        return args.length > 0 && args[0] instanceof JSONObject ? (JSONObject) args[0] : null;
    }

    private void acceptBoard(JSONObject wire) {
        JSONObject data = wire.optJSONObject("data");
        if (data == null) data = wire;
        String stop = ServiceAlerts.boardStop(wire);
        String reason = "";
        if (!"L4".equals(data.optString("line", wire.optString("line", "L4")))) reason = "wrong line";
        else if (data.optJSONArray("via1") == null && data.optJSONArray("via2") == null) reason = "missing via1/via2 arrays";
        if (stop.isEmpty() && attempted.size() == 1) stop = attempted.iterator().next();
        if (reason.isEmpty() && (stop.isEmpty() || !attempted.contains(stop) || !stops.contains(stop)))
            reason = "station was not unambiguously requested";
        if (!reason.isEmpty()) { trace.board(stop, wire, reason, clock.getAsLong()); return; }
        JSONObject board = wire;
        if (ServiceAlerts.boardStop(wire).isEmpty()) {
            try { board = new JSONObject(wire.toString()).put("stopCode", stop); }
            catch (JSONException ignored) { return; }
        }
        lastBoard.put(stop, clock.getAsLong());
        trace.board(stop, wire, "accepted", clock.getAsLong());
        listener.onBoardState(stop, "stopDataUpdate " + stop + " recibido · Socket.IO", false);
        listener.onBoard(stop, board);
    }

    private void syncSubscriptions() {
        Socket current = socket;
        if (!connected || current == null || !current.connected()) return;
        long now = clock.getAsLong();
        for (String stop : stops) {
            Long board = lastBoard.get(stop);
            long gap = board == null ? 20_000 : 40_000;
            Long previous = lastRequest.get(stop);
            if (previous != null && now - previous < gap) continue;
            if (board != null && now - board < 40_000) continue;
            try {
                JSONObject request = new JSONObject().put("line", "L4").put("stop", stop);
                attempted.add(stop);
                firstRequest.putIfAbsent(stop, now);
                lastRequest.put(stop, now);
                trace.state("subscribeStop", request.toString(), now);
                listener.onBoardState(stop, "Suscripción " + stop + " emitida · Socket.IO", false);
                current.emit("subscribeStop", request);
            } catch (JSONException ignored) {}
        }
    }

    void tick() {
        EventThread.exec(() -> {
            if (closed || !connected) return;
            long now = clock.getAsLong();
            String failure = now - lastMap >= 90_000 ? "sin mapa reciente" : "";
            for (String stop : stops) {
                Long requested = firstRequest.get(stop);
                if (requested != null && now - lastBoard.getOrDefault(stop, requested) >= 60_000) {
                    failure = "sin cuadro reciente " + stop;
                    break;
                }
            }
            if (!failure.isEmpty()) {
                trace.state("restart", failure, now);
                listener.onBoardState("", "Socket.IO · " + failure, true);
                open("websocket".equals(transportMode));
            } else syncSubscriptions();
        });
    }

    JSONObject diagnosticSnapshot() {
        JSONObject result = trace.snapshot(clock.getAsLong());
        try {
            JSONArray requested = new JSONArray();
            for (String stop : stops) requested.put(stop);
            result.put("clientLibrary", "socket.io-client-java 2.1.2")
                .put("transportMode", transportMode).put("lastTransportCreated", lastTransport)
                .put("namespaceReady", connected).put("closed", closed).put("requestedStops", requested);
        } catch (JSONException ignored) {}
        return result;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        connected = false;
        watchdog.shutdownNow();
        EventThread.exec(() -> {
            Socket previous = socket;
            socket = null;
            dispose(previous);
            network.dispatcher().cancelAll();
            network.connectionPool().evictAll();
            network.dispatcher().executorService().shutdown();
        });
    }

    private static void dispose(Socket previous) {
        if (previous == null) return;
        previous.io().reconnection(false);
        previous.off();
        previous.offAnyIncoming();
        previous.io().off();
        previous.disconnect();
    }
}
