package com.tmbagent.traineye;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TrainPositionClientTest {
    private static final String HELLO = "0{\"sid\":\"engine-session\",\"pingInterval\":25000,\"pingTimeout\":20000}";
    private static final String ACK = "40{\"sid\":\"namespace-session\"}";
    private static final String MAP = "42[\"mapUpdate\",{\"L4\":{\"trains\":{\"410\":{\"estacion\":\"Girona\",\"platformTrack\":2}}}}]";
    private static final String SUBSCRIBE = "42[\"subscribeStop\",{\"line\":\"L4\",\"stop\":\"425\"}]";
    private static final String BOARD = "42[\"stopDataUpdate\",{\"line\":\"L4\",\"stopCode\":\"425\",\"via1\":[],"
        + "\"via2\":[{\"trainCode\":\"410\",\"tren\":\"410\",\"secsLeft\":181,\"stopCode\":425,\"track\":2}]}]";

    @Test public void pollsMapsDuringBlockedPostAndRecoversMissingBoardOnSameSession() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger handshakes = new AtomicInteger();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger activePosts = new AtomicInteger();
        AtomicInteger maxPosts = new AtomicInteger();
        AtomicInteger mapCount = new AtomicInteger();
        AtomicBoolean ackSent = new AtomicBoolean();
        AtomicBoolean secondPing = new AtomicBoolean();
        AtomicBoolean sentBoard = new AtomicBoolean();
        AtomicBoolean nextStopSubscribed = new AtomicBoolean();
        AtomicBoolean nextBoardSent = new AtomicBoolean();
        CountDownLatch firstPost = new CountDownLatch(1);
        CountDownLatch releasePost = new CountDownLatch(1);
        CountDownLatch mapsWhileBlocked = new CountDownLatch(3);
        CountDownLatch postFailure = new CountDownLatch(1);
        CountDownLatch waitingForBoard = new CountDownLatch(1);
        CountDownLatch found = new CountDownLatch(1);
        CountDownLatch switched = new CountDownLatch(1);
        List<String> writes = new CopyOnWriteArrayList<>();
        List<String> receivedStops = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> handlerError = new AtomicReference<>();
        AtomicLong arrival = new AtomicLong();
        long wallTime = 1_794_252_537_000L;

        server.createContext("/socket.io/", exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                assertTrue(query.startsWith("EIO=4&transport=polling"));
                boolean post = "POST".equals(exchange.getRequestMethod());
                if (!query.contains("sid=")) {
                    assertFalse(post);
                    handshakes.incrementAndGet();
                    send(exchange, 200, HELLO);
                    return;
                }
                assertTrue(query.endsWith("&sid=engine-session"));
                if (post) {
                    maxPosts.accumulateAndGet(activePosts.incrementAndGet(), Math::max);
                    try {
                        assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("text/plain"));
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        writes.add(body);
                        if (SUBSCRIBE.equals(body)) {
                            assertTrue("subscribeStop must follow the namespace ACK", ackSent.get());
                            if (subscriptions.incrementAndGet() == 1) {
                                firstPost.countDown();
                                assertTrue(releasePost.await(3, TimeUnit.SECONDS));
                                send(exchange, 503, "test network interruption");
                                return;
                            }
                        }
                        if (SUBSCRIBE.replace("425", "427").equals(body)) nextStopSubscribed.set(true);
                        send(exchange, 200, "ok");
                    } finally {
                        activePosts.decrementAndGet();
                    }
                    return;
                }

                Thread.sleep(15);
                if (ackSent.compareAndSet(false, true)) {
                    assertEquals(List.of("40"), writes);
                    send(exchange, 200, ACK + "\u001e2\u001e" + MAP);
                } else if (subscriptions.get() >= 3 && sentBoard.compareAndSet(false, true)) {
                    send(exchange, 200, MAP + "\u001e" + BOARD.replace("\"425\"", "\"427\"") + "\u001e" + BOARD);
                } else if (nextStopSubscribed.get() && nextBoardSent.compareAndSet(false, true)) {
                    String ambiguous = "42[\"stopDataUpdate\",{\"via2\":[]}]";
                    send(exchange, 200, BOARD + "\u001e" + ambiguous + "\u001e" + BOARD.replace("425", "427"));
                } else if (firstPost.getCount() == 0 && secondPing.compareAndSet(false, true)) {
                    send(exchange, 200, "2\u001e" + MAP);
                } else {
                    send(exchange, 200, MAP);
                }
            } catch (Throwable error) {
                handlerError.compareAndSet(null, error);
                exchange.close();
            }
        });
        server.start();

        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/socket.io/?EIO=4&transport=polling";
        TrainPositionClient client = new TrainPositionClient(new TrainPositionClient.Listener() {
            @Override public void onMap(JSONObject trains) {
                if ("Girona".equals(trains.optJSONObject("410").optString("estacion"))) {
                    mapCount.incrementAndGet();
                    if (firstPost.getCount() == 0 && releasePost.getCount() == 1) mapsWhileBlocked.countDown();
                }
            }
            @Override public void onState(String status) {}
            @Override public void onBoard(String stop, JSONObject board) {
                receivedStops.add(stop);
                if ("427".equals(stop)) { switched.countDown(); return; }
                ServiceAlerts data = new ServiceAlerts(new ServicePlan(""), wallTime);
                data.acceptBoard(stop, board, wallTime);
                arrival.set(data.predictedArrival("425", "2", "410", wallTime));
                found.countDown();
            }
            @Override public void onBoardState(String stop, String status, boolean error) {
                if (error && status.equals("Suscripción 425 (POST) · HTTP 503")) postFailure.countDown();
                if (status.equals("Esperando stopDataUpdate 425 (GET)")) waitingForBoard.countDown();
            }
        }, endpoint, clock::get);

        client.setStops(Set.of("425"));
        client.start();
        try {
            assertTrue("No subscription was sent", firstPost.await(5, TimeUnit.SECONDS));
            assertTrue("Map polling stalled behind the subscription POST", mapsWhileBlocked.await(2, TimeUnit.SECONDS));
            releasePost.countDown();
            assertTrue("Missing POST error phase", postFailure.await(5, TimeUnit.SECONDS));
            clock.addAndGet(6_000);
            assertTrue("Did not retry the failed POST", waitingForBoard.await(5, TimeUnit.SECONDS));
            clock.addAndGet(21_000);
            assertTrue("Did not re-subscribe after ok without a board", found.await(5, TimeUnit.SECONDS));
            client.setStops(Set.of("427"));
            assertTrue("Changing stops must preserve the Engine.IO session", switched.await(5, TimeUnit.SECONDS));
            assertNull(handlerError.get());
            assertEquals(1, handshakes.get());
            assertEquals(1, maxPosts.get());
            assertTrue(mapCount.get() >= 4);
            assertEquals(List.of("40", "3", SUBSCRIBE, "3", SUBSCRIBE, SUBSCRIBE, SUBSCRIBE.replace("425", "427")), writes);
            assertEquals(List.of("425", "427"), receivedStops);
            assertEquals(wallTime + 181_000, arrival.get());
        } finally {
            releasePost.countDown();
            client.close();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test public void namesTheExactReaderPhaseOnTimeout() throws Exception {
        assertTimeout(0, "Handshake (GET)");
        assertTimeout(1, "Apertura del canal (POST 40)");
        assertTimeout(2, "Confirmación del canal (GET 40)");
        assertTimeout(3, "Espera de eventos (GET)");
    }

    private static void assertTimeout(int failAt, String phase) throws Exception {
        List<String> responses = List.of(HELLO, "ok", ACK);
        AtomicInteger request = new AtomicInteger();
        TrainPositionClient.Transport reader = new TrainPositionClient.Transport() {
            @Override public String request(String sid, String body) throws IOException {
                if ("1".equals(body)) return "ok";
                int index = request.getAndIncrement();
                if (index == failAt) throw new SocketTimeoutException("test timeout");
                return responses.get(index);
            }
            @Override public void cancel() {}
        };
        TrainPositionClient client = new TrainPositionClient(listener(), reader, () -> reader, () -> 0L);
        try {
            client.receiveSession();
            fail("Expected timeout");
        } catch (IOException error) {
            assertEquals(phase + " · SocketTimeoutException", error.getMessage());
            assertTrue(error.getCause() instanceof SocketTimeoutException);
        } finally {
            client.close();
        }
    }

    @Test public void reconnectsWithANewSidAndResubscribes() throws Exception {
        AtomicInteger sessions = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        AtomicReference<CountDownLatch> subscribed = new AtomicReference<>();
        List<String> subscriptionSids = new CopyOnWriteArrayList<>();
        List<String> stops = new CopyOnWriteArrayList<>();
        CountDownLatch secondBoard = new CountDownLatch(1);
        TrainPositionClient.Transport reader = new TrainPositionClient.Transport() {
            @Override public String request(String sid, String body) throws IOException {
                if (sid == null) {
                    polls.set(0);
                    subscribed.set(new CountDownLatch(1));
                    return HELLO.replace("engine-session", "engine-" + sessions.incrementAndGet());
                }
                if (body != null) return "ok";
                int poll = polls.getAndIncrement();
                if (poll == 0) return ACK + "\u001e" + MAP;
                if (poll == 1) {
                    try {
                        if (!subscribed.get().await(2, TimeUnit.SECONDS)) throw new IOException("missing test subscription");
                    } catch (InterruptedException error) { throw new IOException(error); }
                    // Only one stop was requested on this sid, so this nested board can be attributed.
                    return "42[\"stopDataUpdate\",{\"data\":{\"via2\":[]}}]";
                }
                return "1";
            }
            @Override public void cancel() {}
        };
        TrainPositionClient[] holder = new TrainPositionClient[1];
        holder[0] = new TrainPositionClient(new TrainPositionClient.Listener() {
            @Override public void onMap(JSONObject trains) {}
            @Override public void onState(String status) {}
            @Override public void onBoard(String stop, JSONObject board) {
                stops.add(stop);
                if (stops.size() == 2) {
                    holder[0].close();
                    secondBoard.countDown();
                }
            }
        }, reader, () -> new TrainPositionClient.Transport() {
            @Override public String request(String sid, String body) {
                assertEquals(SUBSCRIBE, body);
                subscriptionSids.add(sid);
                subscribed.get().countDown();
                return "ok";
            }
            @Override public void cancel() {}
        }, () -> 0L);
        holder[0].setStops(Set.of("425"));
        holder[0].start();
        try {
            assertTrue(secondBoard.await(7, TimeUnit.SECONDS));
            assertEquals(List.of("engine-1", "engine-2"), subscriptionSids);
            assertEquals(List.of("425", "425"), stops);
        } finally {
            holder[0].close();
        }
    }

    private static TrainPositionClient.Listener listener() {
        return new TrainPositionClient.Listener() {
            @Override public void onMap(JSONObject trains) {}
            @Override public void onState(String status) {}
        };
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
