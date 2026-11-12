package com.tmbagent.traineye;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SocketDiagnosticsTest {
    @Test public void preservesTheRejectedWireFrameWithoutMutatingOrSharingIt() throws Exception {
        SocketDiagnostics trace = new SocketDiagnostics();
        JSONObject raw = new JSONObject("{\"line\":\"L4\",\"stopCode\":\"425\",\"differentRows\":[{\"train\":\"416\"}]}");
        trace.board("425", raw, "missing via1/via2 arrays", 1000);
        raw.put("stopCode", "428");
        JSONObject snapshot = trace.snapshot(3500);
        assertEquals(1, snapshot.getInt("boardFrames"));
        assertEquals(0, snapshot.getInt("acceptedBoards"));
        assertEquals(1, snapshot.getInt("rejectedBoards"));
        assertEquals(2500, snapshot.getJSONObject("lastBoardFrame").getLong("ageMs"));
        assertEquals("425", snapshot.getJSONObject("lastBoardFrame").getJSONObject("raw").getString("stopCode"));
        snapshot.getJSONObject("lastBoardFrame").getJSONObject("raw").put("stopCode", "413");
        assertEquals("425", trace.snapshot(3500).getJSONObject("lastBoardFrame").getJSONObject("raw").getString("stopCode"));
    }

    @Test public void reportsActivePollingAndSubscriptionErrorsAndBoundsTheHistory() throws Exception {
        SocketDiagnostics trace = new SocketDiagnostics();
        trace.session(1000);
        trace.event("mapUpdate");
        trace.event("mapUpdate");
        trace.requestStarted("Espera de eventos (GET)", null, 2000);
        JSONObject snapshot = trace.snapshot(9000);
        assertEquals(2, snapshot.getJSONObject("events").getInt("mapUpdate"));
        assertEquals(7000, snapshot.getJSONObject("activeRequests").getJSONObject("GET").getLong("ageMs"));
        for (int i = 0; i < 40; i++) {
            trace.requestStarted("Suscripción 425 (POST)", "42[\"subscribeStop\",{\"line\":\"L4\",\"stop\":\"425\"}]", 3000 + i);
            trace.requestFinished("Suscripción 425 (POST)", "42", "Suscripción 425 (POST) · HTTP 503", 4000 + i);
        }
        snapshot = trace.snapshot(10000);
        assertEquals(24, snapshot.getJSONArray("history").length());
        assertEquals(40, snapshot.getInt("completedRequests"));
        assertEquals("Suscripción 425 (POST) · HTTP 503",
            snapshot.getJSONArray("history").getJSONObject(23).getString("error"));
        assertFalse(snapshot.getJSONObject("activeRequests").has("POST"));
        assertTrue(snapshot.getJSONObject("activeRequests").has("GET"));
    }

    @Test public void boundsRawFramesAndSeparatesAcceptedFromRejectedBoards() throws Exception {
        SocketDiagnostics trace = new SocketDiagnostics();
        trace.board("413", new JSONObject().put("via1", new org.json.JSONArray()), "accepted", 1000);
        trace.board("", new JSONObject().put("unexpected", "x".repeat(70_000)), "missing via1/via2 arrays", 2000);
        JSONObject snapshot = trace.snapshot(3000);
        assertEquals(1, snapshot.getInt("acceptedBoards"));
        assertEquals(1, snapshot.getInt("rejectedBoards"));
        assertTrue(snapshot.getJSONObject("lastBoardFrame").getBoolean("rawTruncated"));
        assertEquals(65536, snapshot.getJSONObject("lastBoardFrame").getString("rawPrefix").length());
        assertFalse(snapshot.getJSONObject("lastBoardFrame").has("raw"));
    }
}
