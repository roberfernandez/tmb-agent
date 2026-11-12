package com.tmbagent.traineye;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TrainPositionClientTest {
    private static final String HELLO = "0{\"sid\":\"engine-session\",\"pingInterval\":25000,\"pingTimeout\":20000}";
    private static final String ACK = "40{\"sid\":\"namespace-session\"}";
    private static final String MAP = "42[\"mapUpdate\",{\"L4\":{\"trains\":{\"M10\":{\"estacion\":\"Girona\",\"platformTrack\":2}},\"trenesV2\":[{\"trainCode\":\"P10\",\"secPreviousTrain\":977}]}}]";
    private static final String SUBSCRIBE = "42[\"subscribeStop\",{\"line\":\"L4\",\"stop\":\"425\"}]";
    private static final String BOARD = "42[\"stopDataUpdate\",{\"line\":\"L4\",\"stopCode\":\"425\",\"via1\":[],"
        + "\"via2\":[{\"trainCode\":\"P10\",\"tren\":\"P10\",\"secsLeft\":181,\"stopCode\":425,\"track\":2}]}]";

    @Test public void pollsMapsDuringBlockedPostAndRecoversMissingBoardOnSameSession() throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger handshakes = new AtomicInteger();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger activePosts = new AtomicInteger();
        AtomicInteger maxPosts = new AtomicInteger();
        AtomicInteger mapCount = new AtomicInteger();
        AtomicInteger receivedHeadway = new AtomicInteger(-1);
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

        TestServer server = new TestServer(exchange -> {
            try {
                String query = exchange.query;
                assertTrue(query.startsWith("EIO=4&transport=polling"));
                boolean post = "POST".equals(exchange.method);
                if (!query.contains("sid=")) {
                    assertFalse(post);
                    handshakes.incrementAndGet();
                    exchange.send(200, HELLO);
                    return;
                }
                assertTrue(query.endsWith("&sid=engine-session"));
                if (post) {
                    maxPosts.accumulateAndGet(activePosts.incrementAndGet(), Math::max);
                    try {
                        assertTrue(exchange.headers.get("content-type").startsWith("text/plain"));
                        String body = exchange.body;
                        writes.add(body);
                        if (SUBSCRIBE.equals(body)) {
                            assertTrue("subscribeStop must follow the namespace ACK", ackSent.get());
                            if (subscriptions.incrementAndGet() == 1) {
                                firstPost.countDown();
                                assertTrue(releasePost.await(3, TimeUnit.SECONDS));
                                exchange.send(503, "test network interruption");
                                return;
                            }
                        }
                        if (SUBSCRIBE.replace("425", "427").equals(body)) nextStopSubscribed.set(true);
                        exchange.send(200, "ok");
                    } finally {
                        activePosts.decrementAndGet();
                    }
                    return;
                }

                Thread.sleep(15);
                if (ackSent.compareAndSet(false, true)) {
                    assertEquals(List.of("40"), writes);
                    exchange.send(200, ACK + "\u001e2\u001e" + MAP);
                } else if (subscriptions.get() >= 3 && sentBoard.compareAndSet(false, true)) {
                    exchange.send(200, MAP + "\u001e" + BOARD.replace("\"425\"", "\"427\"") + "\u001e" + BOARD);
                } else if (nextStopSubscribed.get() && nextBoardSent.compareAndSet(false, true)) {
                    String ambiguous = "42[\"stopDataUpdate\",{\"via2\":[]}]";
                    exchange.send(200, BOARD + "\u001e" + ambiguous + "\u001e" + BOARD.replace("425", "427"));
                } else if (firstPost.getCount() == 0 && secondPing.compareAndSet(false, true)) {
                    exchange.send(200, "2\u001e" + MAP);
                } else {
                    exchange.send(200, MAP);
                }
            } catch (Throwable error) {
                handlerError.compareAndSet(null, error);
            }
        }, handlerError);
        TrainPositionClient client = new TrainPositionClient(new TrainPositionClient.Listener() {
            @Override public void onMap(JSONObject trains) {
                if ("Girona".equals(trains.optJSONObject("410").optString("estacion"))) {
                    receivedHeadway.set(trains.optJSONObject("410").optInt("secPreviousTrain", -1));
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
        }, server.endpoint(), clock::get);

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
            assertEquals("Map headway lists were lost before reaching the listener", 977, receivedHeadway.get());
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
            server.close();
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

    @Test public void reconnectsForAMissingStationBoardWhileMapsKeepArriving() throws Exception {
        assertStationRecovery("425", false);
    }

    @Test public void reconnectsWhenAPreviouslyWorkingStationBoardStopsUpdating() throws Exception {
        assertStationRecovery("428", true);
    }

    private static void assertStationRecovery(String target, boolean initiallyHasBoard) throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger sessions = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        AtomicInteger maps = new AtomicInteger();
        AtomicReference<CountDownLatch> subscribed = new AtomicReference<>();
        CountDownLatch recovered = new CountDownLatch(1);
        List<String> subscriptionSids = new CopyOnWriteArrayList<>();
        List<String> receivedStops = new CopyOnWriteArrayList<>();
        List<String> errors = new CopyOnWriteArrayList<>();
        AtomicLong arrival = new AtomicLong();
        AtomicLong headway = new AtomicLong(-1);
        long now = 1_794_492_000_000L;
        String unlabelledBoard = "42[\"stopDataUpdate\",{\"data\":{\"via1\":[],\"via2\":["
            + "{\"trainCode\":\"M10\",\"stopCode\":431,\"secsLeft\":900,\"secPreviousTrain\":203}]}}]";
        TrainPositionClient.Transport reader = new TrainPositionClient.Transport() {
            @Override public String request(String sid, String body) throws IOException {
                if (sid == null) {
                    polls.set(0);
                    subscribed.set(new CountDownLatch(1));
                    return HELLO.replace("engine-session", "board-" + sessions.incrementAndGet());
                }
                if (body != null) return "ok";
                int poll = polls.getAndIncrement();
                if (poll == 0) return ACK + "\u001e" + MAP;
                try {
                    if (!subscribed.get().await(2, TimeUnit.SECONDS)) throw new IOException("missing station request");
                } catch (InterruptedException error) { throw new IOException(error); }
                if (sessions.get() == 1) {
                    if (initiallyHasBoard && poll == 1) {
                        return BOARD.replace("425", target);
                    }
                    // Position events stay fresh. Only the station board has stopped.
                    clock.addAndGet(60_001);
                    return MAP;
                }
                return unlabelledBoard;
            }
            @Override public void cancel() {}
        };
        TrainPositionClient[] holder = new TrainPositionClient[1];
        holder[0] = new TrainPositionClient(new TrainPositionClient.Listener() {
            @Override public void onMap(JSONObject trains) { maps.incrementAndGet(); }
            @Override public void onState(String status) {}
            @Override public void onBoardState(String stop, String status, boolean error) {
                if (error) errors.add(status);
            }
            @Override public void onBoard(String stop, JSONObject board) {
                receivedStops.add(stop);
                if (sessions.get() < 2) return;
                ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), now);
                boards.acceptBoard(stop, board, now);
                arrival.set(boards.predictedArrival(target, "2", "410", now));
                StationHeadway intervals = new StationHeadway();
                intervals.accept(stop, board, now);
                headway.set(intervals.seconds(target, "", "P10", now));
                holder[0].close();
                recovered.countDown();
            }
        }, reader, () -> new TrainPositionClient.Transport() {
            @Override public String request(String sid, String body) {
                subscriptionSids.add(sid);
                subscribed.get().countDown();
                return "ok";
            }
            @Override public void cancel() {}
        }, clock::get);
        holder[0].setStops(Set.of(target));
        holder[0].start();
        try {
            assertTrue("Fresh maps hid the missing station board", recovered.await(8, TimeUnit.SECONDS));
            assertEquals(2, sessions.get());
            assertTrue(subscriptionSids.contains("board-1"));
            assertTrue(subscriptionSids.contains("board-2"));
            assertTrue(maps.get() >= 3);
            assertTrue(errors.stream().anyMatch(error -> error.contains("Cuadro " + target)));
            assertTrue(receivedStops.stream().allMatch(target::equals));
            assertEquals(initiallyHasBoard ? 2 : 1, receivedStops.size());
            assertEquals("An unlabelled single-station board lost its verified scope", now + 900_000L, arrival.get());
            assertEquals(203, headway.get());
            JSONObject diagnostics = holder[0].diagnosticSnapshot();
            assertEquals(2, diagnostics.getInt("sessionCount"));
            assertTrue(diagnostics.getJSONObject("events").getInt("mapUpdate") >= 3);
            assertEquals(initiallyHasBoard ? 2 : 1, diagnostics.getInt("acceptedBoards"));
            assertEquals("accepted", diagnostics.getJSONObject("lastBoardFrame").getString("decision"));
            assertEquals(target, diagnostics.getJSONObject("lastBoardFrame").getString("stop"));
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

    // A loopback server using the same Java networking APIs exposed to Android's unit tests.
    private static final class TestServer implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        private final ExecutorService workers = Executors.newCachedThreadPool();

        TestServer(Consumer<Exchange> handler, AtomicReference<Throwable> error) throws IOException {
            workers.execute(() -> {
                try {
                    while (!server.isClosed()) {
                        Socket socket = server.accept();
                        workers.execute(() -> {
                            try (Socket client = socket) {
                                client.setSoTimeout(3000);
                                handler.accept(new Exchange(client));
                            } catch (IOException | RuntimeException failure) {
                                if (!server.isClosed()) error.compareAndSet(null, failure);
                            }
                        });
                    }
                } catch (IOException failure) {
                    if (!server.isClosed()) error.compareAndSet(null, failure);
                }
            });
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/socket.io/?EIO=4&transport=polling";
        }

        @Override public void close() throws IOException {
            server.close();
            workers.shutdownNow();
        }
    }

    private static final class Exchange {
        final String method;
        final String query;
        final Map<String, String> headers = new HashMap<>();
        final String body;
        private final Socket socket;

        Exchange(Socket socket) throws IOException {
            this.socket = socket;
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            String[] request = readLine(input).split(" ", 3);
            method = request[0];
            query = URI.create(request[1]).getRawQuery();
            for (String line = readLine(input); !line.isEmpty(); line = readLine(input)) {
                int separator = line.indexOf(':');
                headers.put(line.substring(0, separator).toLowerCase(Locale.ROOT), line.substring(separator + 1).trim());
            }
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            body = new String(bytes, StandardCharsets.UTF_8);
        }

        void send(int status, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Service Unavailable")
                + "\r\nConnection: close\r\nContent-Type: text/plain\r\nContent-Length: " + bytes.length + "\r\n\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        private static String readLine(InputStream input) throws IOException {
            StringBuilder line = new StringBuilder();
            for (int value = input.read(); value != -1; value = input.read()) {
                if (value == '\n') return line.toString();
                if (value != '\r') line.append((char) value);
            }
            throw new IOException("End of test HTTP request");
        }
    }
}
