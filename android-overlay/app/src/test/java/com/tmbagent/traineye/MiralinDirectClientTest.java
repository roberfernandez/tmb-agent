package com.tmbagent.traineye;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class MiralinDirectClientTest {
    @Test public void subscribesToStationBoardsAgainAfterReconnect() throws Exception {
        String board = "42[\"stopDataUpdate\",{\"stopCode\":\"423\",\"via1\":[]}]";
        FakeTransport transport = new FakeTransport(HELLO, "40{}", board, "1", HELLO, "40{}", board) {
            @Override public String request(String session, String body) throws IOException {
                if (session == null && next == 4) return packets.get(next++);
                return super.request(session, body);
            }
        };
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        int[] count = {0};
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String status) {}
            public void onBoard(JSONObject board) { if (++count[0] == 2) client[0].close(); }
        }, transport);
        client[0].setStops(java.util.Collections.singleton("423"));
        try { client[0].receiveSession(); fail(); } catch (IOException expected) {}
        client[0].receiveSession();
        assertEquals(2, count[0]);
        assertEquals(2, transport.writes.stream().filter(body -> body.contains("subscribeStop")).count());
    }
    private static final String HELLO = "0{\"sid\":\"test-session\",\"pingInterval\":25000,\"pingTimeout\":20000}";
    private static final String MAP = "42[\"mapUpdate\",{\"L4\":{\"trains\":{\"405\":{\"estacion\":\"Jaume I\",\"platformTrack\":1}}}}]";

    private static class FakeTransport implements MiralinDirectClient.Transport {
        final List<String> packets;
        final List<String> writes = new ArrayList<>();
        int next;
        boolean cancelled;
        FakeTransport(String... packets) { this.packets = Arrays.asList(packets); }
        public String request(String session, String body) throws IOException {
            if (body != null) {
                assertEquals("test-session", session);
                writes.add(body);
                return "ok";
            }
            if (next == 0) assertNull(session);
            else assertEquals("test-session", session);
            if (next >= packets.size()) throw new IOException("End of test");
            return packets.get(next++);
        }
        public void cancel() { cancelled = true; }
    }

    @Test public void receivesTwoMapsAndRespondsToHeartbeat() throws Exception {
        FakeTransport transport = new FakeTransport(HELLO, "40{}\u001e2\u001e" + MAP, MAP);
        List<JSONObject> maps = new ArrayList<>();
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { maps.add(trains); if (maps.size() == 2) client[0].close(); }
            public void onState(String state) {}
        }, transport);
        client[0].receiveSession();
        assertEquals(2, maps.size());
        assertEquals("Jaume I", maps.get(0).getJSONObject("405").getString("estacion"));
        assertEquals(Arrays.asList("40", "3"), transport.writes);
        assertTrue(transport.cancelled);
    }

    @Test public void handshakeAloneDoesNotCountAsTrainData() throws Exception {
        FakeTransport transport = new FakeTransport(HELLO, "40{}", "1");
        List<JSONObject> maps = new ArrayList<>();
        MiralinDirectClient client = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { maps.add(trains); }
            public void onState(String state) { assertFalse("dades rebudes".equals(state)); }
        }, transport);
        try { client.receiveSession(); fail("Expected closed session"); }
        catch (IOException expected) { assertEquals("Session closed", expected.getMessage()); }
        assertTrue(maps.isEmpty());
        assertEquals(Arrays.asList("40", "1"), transport.writes);
        client.close();
    }

    @Test public void rejectsMissingSessionWithoutSendingConnect() throws Exception {
        FakeTransport transport = new FakeTransport("0{}");
        MiralinDirectClient client = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String state) {}
        }, transport);
        try { client.receiveSession(); fail("Expected missing session"); }
        catch (IOException expected) { assertEquals("Missing session", expected.getMessage()); }
        assertTrue(transport.writes.isEmpty());
        client.close();
    }

    @Test public void reconnectsAfterNetworkFailureAndStops() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        FakeTransport transport = new FakeTransport(HELLO, MAP) {
            int attempts;
            @Override public String request(String session, String body) throws IOException {
                if (session == null && ++attempts == 1) throw new IOException("Simulated network loss");
                return super.request(session, body);
            }
        };
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { client[0].close(); received.countDown(); }
            public void onState(String state) {}
        }, transport);
        client[0].start();
        try { assertTrue("Did not reconnect", received.await(8, TimeUnit.SECONDS)); }
        finally { client[0].close(); }
        assertTrue(transport.cancelled);
    }
    @Test public void attributesUnlabelledBoardOnlyWithOneSubscribedStation() throws Exception {
        String packet = "42[\"stopDataUpdate\",{\"via1\":[{\"trainCode\":\"407\",\"secsLeft\":300}]}]";
        FakeTransport transport = new FakeTransport(HELLO, "40{}", packet);
        List<JSONObject> received = new ArrayList<>();
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String status) {}
            public void onBoard(JSONObject board) { received.add(board); client[0].close(); }
        }, transport);
        client[0].setStops(java.util.Collections.singleton("413"));
        client[0].receiveSession();
        assertEquals(1, received.size());
        assertEquals("413", received.get(0).getString("stopCode"));
        long now = 1800000000000L;
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST,413,413,,,;630,690,CON,413,434,407,1,1"), now);
        alerts.acceptBoard(received.get(0), now);
        assertEquals(now + 300000L, alerts.predictedArrival("413", "1", "407", now));
    }

    @Test public void neverGuessesStationWhenSeveralAreSubscribed() throws Exception {
        String packet = "42[\"stopDataUpdate\",{\"via1\":[{\"trainCode\":\"407\",\"secsLeft\":300}]}]";
        FakeTransport transport = new FakeTransport(HELLO, packet);
        List<JSONObject> received = new ArrayList<>();
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String status) {}
            public void onBoard(JSONObject board) { received.add(board); client[0].close(); }
        }, transport);
        client[0].setStops(java.util.Set.of("413", "423"));
        client[0].receiveSession();
        assertEquals(1, received.size());
        assertFalse(received.get(0).has("stopCode"));
    }

    @Test public void attributesNestedUnlabelledBoardToLaPau() throws Exception {
        String packet = "42[\"stopDataUpdate\",{\"data\":{\"via1\":[{\"trainCode\":\"407\",\"secsLeft\":240}]}}]";
        FakeTransport transport = new FakeTransport(HELLO, packet);
        List<JSONObject> received = new ArrayList<>();
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String status) {}
            public void onBoard(JSONObject board) { received.add(board); client[0].close(); }
        }, transport);
        client[0].setStops(java.util.Collections.singleton("413"));
        client[0].receiveSession();
        long now = 1800000000000L;
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST,413,413,,,;630,690,CON,413,434,407,1,1"), now);
        alerts.acceptBoard(received.get(0), now);
        assertEquals(now + 240000L, alerts.predictedArrival("413", "1", "407", now));
    }

    @Test public void reconnectsWhenOldStationSubscriptionIsNoLongerNeeded() throws Exception {
        String board = "42[\"stopDataUpdate\",{\"stopCode\":\"413\",\"via1\":[]}]";
        FakeTransport transport = new FakeTransport(HELLO, "40{}", board, board);
        MiralinDirectClient[] client = new MiralinDirectClient[1];
        int[] received = {0};
        client[0] = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            public void onMap(JSONObject trains) { fail(); }
            public void onState(String state) {}
            public void onBoard(JSONObject data) {
                received[0]++;
                client[0].setStops(java.util.Collections.singleton("434"));
            }
        }, transport);
        client[0].setStops(java.util.Collections.singleton("413"));
        try {
            client[0].receiveSession();
            fail("Expected reconnect after dropping La Pau subscription");
        } catch (IOException expected) {
            assertEquals("Station subscriptions changed", expected.getMessage());
        } finally { client[0].close(); }
        assertEquals(1, received[0]);
        assertEquals(1, transport.writes.stream().filter(x -> x.contains("subscribeStop") && x.contains("413")).count());
    }

}
