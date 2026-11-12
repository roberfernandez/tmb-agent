package com.tmbagent.traineye;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketIoConnectionTest {
    private static final long NOW = 1_794_470_000_000L;
    private static final String HELLO = "0{\"sid\":\"engine-session\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":20000}";
    private static final String ACK = "40{\"sid\":\"namespace-session\"}";

    private static String map() throws Exception {
        JSONObject line = new JSONObject()
            .put("trains", new JSONObject().put("M16", new JSONObject().put("estacion", "Bogatell").put("platformTrack", 1)))
            .put("trenesV1", new JSONArray().put(new JSONObject().put("trainCode", "P16").put("secPreviousTrain", 289)));
        return "42" + new JSONArray().put("mapUpdate").put(new JSONObject().put("L4", line));
    }

    private static String board(String stop) throws Exception {
        JSONObject row = new JSONObject().put("trainCode", "M16").put("stopCode", 420)
            .put("arrivalTime", NOW + 600_000L).put("secsLeft", 600).put("secPreviousTrain", 203);
        JSONObject data = new JSONObject().put("line", "L4").put("stopCode", stop)
            .put("via1", new JSONArray().put(row)).put("via2", new JSONArray());
        return "42" + new JSONArray().put("stopDataUpdate").put(data);
    }

    private static final class Observer implements TrainPositionClient.Listener {
        final CountDownLatch map = new CountDownLatch(1), first = new CountDownLatch(1), second = new CountDownLatch(1);
        final List<String> stops = new CopyOnWriteArrayList<>();
        final AtomicLong arrival = new AtomicLong(), headway = new AtomicLong(-1);
        @Override public void onState(String status) {}
        @Override public void onMap(JSONObject trains) {
            if (trains.has("416")) map.countDown();
        }
        @Override public void onBoard(String stop, JSONObject board) {
            ServiceAlerts arrivals = new ServiceAlerts(new ServicePlan(""), NOW);
            arrivals.acceptBoard(stop, board, NOW);
            arrival.set(arrivals.predictedArrival(stop, "1", "416", NOW));
            StationHeadway intervals = new StationHeadway();
            intervals.accept(stop, board, NOW);
            headway.set(intervals.seconds(stop, "", "416", NOW));
            stops.add(stop);
            if (stops.size() == 1) first.countDown(); else second.countDown();
        }
    }

    private static final class Peer extends Dispatcher {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicReference<WebSocket> live = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch pong = new CountDownLatch(1);
        @Override public MockResponse dispatch(RecordedRequest request) {
            if (!request.getPath().contains("transport=websocket")) return new MockResponse().setResponseCode(503);
            return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override public void onOpen(WebSocket socket, Response response) {
                    connections.incrementAndGet();
                    live.set(socket);
                    socket.send(HELLO);
                }
                @Override public void onMessage(WebSocket socket, String message) {
                    try {
                        if ("40".equals(message)) {
                            socket.send(ACK);
                            socket.send(map());
                            socket.send("2");
                        } else if ("3".equals(message)) {
                            pong.countDown();
                        } else if (message.startsWith("42")) {
                            JSONArray event = new JSONArray(message.substring(2));
                            if (!"subscribeStop".equals(event.optString(0))) return;
                            JSONObject selected = event.getJSONObject(1);
                            assertEquals("L4", selected.getString("line"));
                            String stop = selected.getString("stop");
                            // Delayed data for the old station must not contaminate a changed target.
                            if ("428".equals(stop)) socket.send(board("425"));
                            socket.send(board(stop));
                        }
                    } catch (Throwable error) { failure.compareAndSet(null, error); }
                }
                @Override public void onClosing(WebSocket socket, int code, String reason) { socket.close(code, reason); }
            });
        }
    }

    @Test public void nativeEntryUsesWebSocketForMapBoardsHeartbeatAndStationChanges() throws Exception {
        MockWebServer server = new MockWebServer();
        Peer peer = new Peer();
        server.setDispatcher(peer);
        server.start();
        Observer observer = new Observer();
        TrainPositionClient client = new TrainPositionClient(observer, server.url("/").uri(),
            () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        client.setStops(Set.of("425"));
        client.start();
        try {
            assertTrue(observer.first.await(8, TimeUnit.SECONDS));
            assertTrue(observer.map.await(2, TimeUnit.SECONDS));
            assertTrue(peer.pong.await(2, TimeUnit.SECONDS));
            assertEquals(NOW + 600_000L, observer.arrival.get());
            assertEquals(203, observer.headway.get());
            client.setStops(Set.of("428"));
            assertTrue(observer.second.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("425", "428"), observer.stops);
            assertEquals(1, peer.connections.get());
            assertNull(peer.failure.get());
            JSONObject diagnostics = client.diagnosticSnapshot();
            assertEquals("socket.io-client-java 2.1.2", diagnostics.getString("clientLibrary"));
            assertEquals("websocket", diagnostics.getString("transportMode"));
            assertTrue(diagnostics.getBoolean("namespaceReady"));
            assertEquals("428", diagnostics.getJSONArray("requestedStops").getString(0));
            assertEquals(1, diagnostics.getInt("rejectedBoards"));
        } finally { client.close(); server.shutdown(); }
    }

    @Test public void reconnectsAndResubscribesToTheSelectedStation() throws Exception {
        MockWebServer server = new MockWebServer();
        Peer peer = new Peer();
        server.setDispatcher(peer);
        server.start();
        Observer observer = new Observer();
        SocketIoConnection client = new SocketIoConnection(observer, server.url("/").uri(),
            () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), 100);
        client.setStops(Set.of("434"));
        client.start();
        try {
            assertTrue(observer.first.await(8, TimeUnit.SECONDS));
            peer.live.get().close(1001, "test interruption");
            assertTrue("Reconnect did not deliver a second board: " + client.diagnosticSnapshot(), observer.second.await(8, TimeUnit.SECONDS));
            assertEquals(List.of("434", "434"), observer.stops);
            assertEquals(2, peer.connections.get());
            assertNull(peer.failure.get());
            assertEquals(2, client.diagnosticSnapshot().getInt("sessionCount"));
        } finally { client.close(); server.shutdown(); }
    }

    @Test public void restartsAStalledBoardEvenWhenItsMapIsRecent() throws Exception {
        MockWebServer server = new MockWebServer();
        Peer peer = new Peer();
        server.setDispatcher(peer);
        server.start();
        Observer observer = new Observer();
        AtomicLong clock = new AtomicLong(1000);
        SocketIoConnection client = new SocketIoConnection(observer, server.url("/").uri(), clock::get, 100);
        client.setStops(Set.of("413"));
        client.start();
        try {
            assertTrue(observer.first.await(8, TimeUnit.SECONDS));
            clock.addAndGet(60_001);
            client.tick();
            assertTrue(observer.second.await(8, TimeUnit.SECONDS));
            assertEquals(List.of("413", "413"), observer.stops);
            assertTrue(client.diagnosticSnapshot().toString().contains("sin cuadro reciente 413"));
            assertNull(peer.failure.get());
        } finally { client.close(); server.shutdown(); }
    }

    @Test public void usesStandardPollingFallbackWhenWebSocketIsUnavailable() throws Exception {
        MockWebServer server = new MockWebServer();
        AtomicInteger blocked = new AtomicInteger();
        AtomicBoolean ackReady = new AtomicBoolean(), ackSent = new AtomicBoolean(), boardReady = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                try {
                    if (request.getPath().contains("transport=websocket")) {
                        blocked.incrementAndGet();
                        return new MockResponse().setResponseCode(503).setBody("WebSocket disabled for test");
                    }
                    if ("POST".equals(request.getMethod())) {
                        for (String packet : request.getBody().readUtf8().split("\u001e")) {
                            if ("40".equals(packet)) ackReady.set(true);
                            else if (packet.startsWith("42")) {
                                JSONArray event = new JSONArray(packet.substring(2));
                                assertEquals("subscribeStop", event.getString(0));
                                assertEquals("426", event.getJSONObject(1).getString("stop"));
                                boardReady.set(true);
                            }
                        }
                        return new MockResponse().setBody("ok");
                    }
                    if (!request.getPath().contains("sid=")) return new MockResponse().setBody(HELLO);
                    if (ackReady.get() && ackSent.compareAndSet(false, true)) {
                        return new MockResponse().setBody(ACK + "\u001e" + map());
                    }
                    if (boardReady.compareAndSet(true, false)) return new MockResponse().setBody(board("426"));
                    return new MockResponse().setBody("2").setBodyDelay(30, TimeUnit.MILLISECONDS);
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                    return new MockResponse().setResponseCode(500);
                }
            }
        });
        server.start();
        Observer observer = new Observer();
        SocketIoConnection client = new SocketIoConnection(observer, server.url("/").uri(),
            () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), 100);
        client.setStops(Set.of("426"));
        client.start();
        try {
            assertTrue("Polling fallback did not recover the board", observer.first.await(12, TimeUnit.SECONDS));
            assertEquals(3, blocked.get());
            assertEquals(203, observer.headway.get());
            assertEquals(NOW + 600_000L, observer.arrival.get());
            assertEquals("polling+upgrade", client.diagnosticSnapshot().getString("transportMode"));
            assertTrue(client.diagnosticSnapshot().toString().contains("503"));
            assertNull(failure.get());
        } finally { client.close(); server.shutdown(); }
    }
}
