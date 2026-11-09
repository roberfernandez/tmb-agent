package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/** One Engine.IO polling session, owned by the active foreground service. */
final class MiralinDirectClient implements AutoCloseable {
    // Verified against live L4 station-board capture (La Pau, stop 413):
    // stopDataUpdate carries trainCode and arrivalTime for the selected stop.
    private static final String ENDPOINT = "https://miralin.cat/socket.io/?EIO=4&transport=polling";
    private static final long STALE_NANOS = 90_000_000_000L;

    interface Listener {
        void onMap(JSONObject trains);
        void onState(String status);
        default void onBoard(JSONObject board) {}
    }

    interface Transport {
        String request(String session, String body) throws Exception;
        void cancel();
    }

    private final Listener listener;
    private final Transport transport;
    private volatile boolean closed;
    private Thread worker;
    private boolean sessionHadMap;
    private volatile Set<String> stops = Collections.emptySet();

    void setStops(Set<String> requested) { stops = new HashSet<>(requested); }

    MiralinDirectClient(Listener listener) { this(listener, new HttpsTransport()); }

    // Injecting transport lets the actual protocol run in JVM tests without a live feed.
    MiralinDirectClient(Listener listener, Transport transport) {
        this.listener = listener;
        this.transport = transport;
    }

    synchronized void start() {
        if (worker != null || closed) return;
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
            } catch (Exception error) {
                if (!closed) listener.onState("Miralin directe · " + error.getClass().getSimpleName());
            }
            if (sessionHadMap) retry = 2000;
            if (!closed) {
                try { Thread.sleep(retry + ThreadLocalRandom.current().nextInt(1000)); }
                catch (InterruptedException ignored) { if (closed) break; }
                retry = Math.min(retry * 2, 30000);
            }
        }
    }

    void receiveSession() throws Exception {
        String hello = transport.request(null, null);
        if (!hello.startsWith("0")) throw new IOException("Invalid handshake");
        String session = new JSONObject(hello.substring(1)).optString("sid", "");
        if (session.isEmpty()) throw new IOException("Missing session");
        try {
            transport.request(session, "40");
            long lastMap = System.nanoTime();
            Set<String> subscribed = new HashSet<>();
            while (!closed) {
                // Socket subscriptions persist for the lifetime of a session.
                // Reconnect when a station is no longer requested, otherwise
                // stale subscriptions make unlabelled station boards ambiguous.
                if (!stops.containsAll(subscribed)) {
                    throw new IOException("Station subscriptions changed");
                }
                for (String stop : stops) {
                    if (!stop.matches("41[3-9]|42[0-9]|43[0-4]") || !subscribed.add(stop)) continue;
                    transport.request(session, "42" + new JSONArray().put("subscribeStop").put(new JSONObject().put("line", "L4").put("stop", stop)).toString());
                }
                String payload = transport.request(session, null);
                for (String packet : payload.split("\u001e")) {
                    if (closed) return;
                    if (packet.equals("2")) transport.request(session, "3");
                    else if (packet.equals("1") || packet.startsWith("41")) throw new IOException("Session closed");
                    else if (packet.startsWith("44")) throw new IOException("Connection rejected");
                    else if (packet.startsWith("42")) {
                        JSONArray event = new JSONArray(packet.substring(2));
                        if ("stopDataUpdate".equals(event.optString(0))) {
                            JSONObject board = event.optJSONObject(1);
                            if (board != null) {
                                // An unlabelled board can only be assigned when
                                // exactly one station is subscribed.
                                JSONObject data = board.optJSONObject("data");
                                if (data == null) data = board;
                                boolean identified = false;
                                for (String field : new String[]{"stopCode", "stop", "stopId", "stationId", "stationCode", "id"}) {
                                    Object value = data.opt(field);
                                    if (value instanceof JSONObject) {
                                        JSONObject nested = (JSONObject) value;
                                        value = nested.opt("code");
                                        if (value == null) value = nested.opt("id");
                                    }
                                    if (value != null && String.valueOf(value).matches("41[3-9]|42[0-9]|43[0-4]")) {
                                        identified = true;
                                        break;
                                    }
                                }
                                if (!identified && subscribed.size() == 1) {
                                    JSONObject tagged = new JSONObject(board.toString());
                                    tagged.put("stopCode", subscribed.iterator().next());
                                    listener.onBoard(tagged);
                                } else {
                                    listener.onBoard(board);
                                }
                            }
                            continue;
                        }
                        if (!"mapUpdate".equals(event.optString(0))) continue;
                        JSONObject data = event.optJSONObject(1);
                        JSONObject line = data == null ? null : data.optJSONObject("L4");
                        JSONObject trains = line == null ? null : line.optJSONObject("trains");
                        if (trains != null) {
                            sessionHadMap = true;
                            lastMap = System.nanoTime();
                            listener.onMap(trains);
                            listener.onState("Miralin directe · dades rebudes");
                        }
                    }
                }
                if (System.nanoTime() - lastMap > STALE_NANOS) throw new IOException("Stale feed");
            }
        } finally {
            // The reader has finished before sending close; never overlap polling GETs/POSTs.
            if (!closed) {
                try { transport.request(session, "1"); } catch (Exception ignored) {}
            }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        transport.cancel();
        if (worker != null) worker.interrupt();
    }

    private static final class HttpsTransport implements Transport {
        private volatile HttpURLConnection active;
        private volatile boolean cancelled;

        @Override public String request(String session, String body) throws Exception {
            if (cancelled) throw new IOException("Stopped");
            String url = ENDPOINT + (session == null ? "" : "&sid=" + URLEncoder.encode(session, "UTF-8"));
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            active = connection;
            try {
                if (cancelled) throw new IOException("Stopped");
                boolean closing = "1".equals(body);
                connection.setConnectTimeout(closing ? 2000 : 10000);
                connection.setReadTimeout(closing ? 2000 : 40000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(body == null ? "GET" : "POST");
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
                    connection.setFixedLengthStreamingMode(bytes.length);
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
