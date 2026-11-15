package com.tmbagent.traineye;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ManualTrainEtaTest {
    private static final long NOW = 1_794_317_508_000L;

    @Test public void followsOnlySelectedTrackEvenWhenOtherTrackArrivesEarlier() throws JSONException {
        ManualTrainEta via1 = new ManualTrainEta("423", "1", "410", false);
        ManualTrainEta via2 = new ManualTrainEta("423", "2", "410", false);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);
        assertEquals(NOW + 2_893_000, via1.predictedArrival(boards, NOW, null, 0));
        assertEquals(NOW + 411_000, via2.predictedArrival(boards, NOW, null, 0));

        boards.acceptBoard(board(202, 1100), NOW + 1_000);
        assertEquals(NOW + 203_000, via1.predictedArrival(boards, NOW + 1_000, null, 0));
        assertEquals(NOW + 1_101_000, via2.predictedArrival(boards, NOW + 1_000, null, 0));
    }

    @Test public void retainsRecentBoardOnlyWhileApproaching() throws JSONException {
        ManualTrainEta eta = new ManualTrainEta("423", "2", "410", false);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);

        JSONObject via2 = position("Verdaguer", 2);
        assertEquals(NOW + 411_000, eta.predictedArrival(boards, NOW + 60_000, via2, 2_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 61_000, via2, 15_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 61_000, position("Barceloneta", 1), 2_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 120_000, via2, 2_000));
    }

    @Test public void manualWatchRearamsAfterEveryPassWithoutRestart() throws JSONException {
        ManualTrainEta eta = new ManualTrainEta("423", "1", "410", false);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);
        assertEquals(NOW + 2_893_000L, eta.predictedArrival(boards, NOW, null, 0));
        assertTrue(eta.observe(position("Jaume I", 1)));
        assertTrue(eta.isWaiting());
        assertEquals(0, eta.predictedArrival(boards, NOW, position("Jaume I", 1), 0));
        assertFalse(eta.observe(position("Urquinaona", 1)));
        assertEquals(NOW + 2_893_000L, eta.predictedArrival(boards, NOW, null, 0));
        assertTrue("Another circuit triggers an arrival without restarting the watch",
            eta.observe(position("Jaume I", 1)));
        assertTrue(eta.isWaiting());
    }

    @Test public void previouslyCompletedManualTrackingNoLongerBlocksEta() throws JSONException {
        ManualTrainEta eta = new ManualTrainEta("423", "2", "410", true);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);
        assertTrue(eta.isWaiting());
        assertEquals(NOW + 411_000L, eta.predictedArrival(boards, NOW, null, 0));
    }

    @Test public void laPauUsesTheStationForecastEvenWhenPublishedOnVia1() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = new JSONObject().put("stopCode", "413")
            .put("via1", new org.json.JSONArray().put(new JSONObject()
                .put("trainCode", "M06").put("stopCode", "415").put("secsLeft", 4200)))
            .put("via2", new org.json.JSONArray())
            .put("lineTrains", new org.json.JSONArray().put(new JSONObject()
                .put("trainCode", "M06").put("stopCode", "415")
                .put("platformTrack", 1).put("secsLeft", 47)));
        boards.acceptBoard(raw, NOW);
        ManualTrainEta eta = new ManualTrainEta("413", "", "406", false);
        assertEquals(NOW + 4_200_000L,
            eta.predictedArrival(boards, NOW, position("Besòs Mar", 1), 0));
        assertFalse(eta.observe(position("Besòs Mar", 1)));

        raw.put("via1", new org.json.JSONArray());
        boards.acceptBoard(raw, NOW);
        assertEquals(0, eta.predictedArrival(boards, NOW, position("Besòs Mar", 1), 0));
    }

    @Test public void selectedTrackDoesNotChangeWithTrainsDirection() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        ManualTrainEta via1 = new ManualTrainEta("423", "1", "P10", false);
        ManualTrainEta via2 = new ManualTrainEta("423", "2", "P10", false);
        boards.acceptBoard(board(47, 900), NOW);
        assertEquals(NOW + 47_000L,
            via1.predictedArrival(boards, NOW, position("Girona", 1), 0));
        assertEquals(NOW + 900_000L,
            via2.predictedArrival(boards, NOW, position("Girona", 1), 0));

        boards.acceptBoard(board(900, 47), NOW);
        assertEquals(NOW + 900_000L,
            via1.predictedArrival(boards, NOW, position("Barceloneta", 2), 0));
        assertEquals(NOW + 47_000L,
            via2.predictedArrival(boards, NOW, position("Barceloneta", 2), 0));
    }

    @Test public void intermediateStationsRequireViaAndIgnorePassesOnOtherTrack() throws JSONException {
        assertThrows(IllegalArgumentException.class, () -> new ManualTrainEta("425", "", "410", false));
        ManualTrainEta eta = new ManualTrainEta("425", "2", "410", false);
        assertFalse(eta.observe(position("Passeig de Gràcia", 1)));
        assertTrue(eta.isWaiting());
        assertTrue(eta.observe(position("Passeig de Gràcia", 2)));
        assertTrue(eta.isWaiting());
        assertEquals(0, eta.predictedArrival(new ServiceAlerts(new ServicePlan(""), NOW), NOW,
            position("Passeig de Gràcia", 2), 0));
        assertFalse(eta.observe(position("Girona", 2)));
        assertTrue(eta.observe(position("Passeig de Gràcia", 2)));
    }

    @Test public void trinitatUsesItsStationForecastInsteadOfThePositionTimer() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = new JSONObject().put("stopCode", "434")
            .put("via1", new org.json.JSONArray().put(new JSONObject()
                .put("trainCode", "M13").put("stopCode", "433").put("secsLeft", 600)))
            .put("via2", new org.json.JSONArray())
            .put("lineTrains", new org.json.JSONArray().put(new JSONObject()
                .put("trainCode", "M13").put("stopCode", "433")
                .put("platformTrack", 2).put("secsLeft", 30)));
        boards.acceptBoard(raw, NOW);
        ManualTrainEta eta = new ManualTrainEta("434", "", "413", false);
        assertEquals(NOW + 600_000L,
            eta.predictedArrival(boards, NOW, position("Via Júlia", 2), 0));
    }

    private static JSONObject board(int via1, int via2) throws JSONException {
        return new JSONObject("{\"line\":\"L4\",\"stopCode\":\"423\","
            + "\"via1\":[{\"trainCode\":\"409\",\"secsLeft\":45},{\"trainCode\":\"410\",\"secsLeft\":" + via1 + "}],"
            + "\"via2\":[{\"trainCode\":\"410\",\"secsLeft\":" + via2 + "}]}");
    }

    private static JSONObject position(String station, int track) throws JSONException {
        return new JSONObject().put("estacion", station).put("platformTrack", track);
    }
}
