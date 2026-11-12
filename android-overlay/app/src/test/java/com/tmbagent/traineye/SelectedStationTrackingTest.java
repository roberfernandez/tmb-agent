package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Regression checks for the selected station, independently of the train's map location. */
public class SelectedStationTrackingTest {
    private static final long NOW = 1_794_467_869_000L;

    @Test public void followsTheSelectedTrainAtEveryL4StationAndCanChangeTheTarget() throws Exception {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        StationHeadway headway = new StationHeadway();
        for (int id = 413; id <= 434; id++) {
            String target = Integer.toString(id);
            JSONObject board = forecast(target, 4000, 2111);
            boards.acceptBoard(target, board, NOW);
            headway.accept(target, board, NOW);
            ManualTrainEta manual = new ManualTrainEta(target, id % 2 == 0 ? "M16" : "P16", false);
            assertEquals("ETA selected stop " + target, NOW + 2_111_000L,
                manual.predictedArrival(boards, NOW, null, 0));
            assertEquals("Headway selected stop " + target, 203, headway.seconds(target, "", "416", NOW));
            assertEquals(target, manual.stop);
            assertTrue(manual.isWaiting());
        }

        // A refreshed Joanic board must not replace a previously selected Girona board.
        boards.acceptBoard("428", forecast("428", 300, 600), NOW);
        headway.accept("428", forecast("428", 300, 600), NOW);
        assertEquals(NOW + 300_000L, new ManualTrainEta("428", "416", false)
            .predictedArrival(boards, NOW, null, 0));
        assertEquals(NOW + 2_111_000L, new ManualTrainEta("426", "416", false)
            .predictedArrival(boards, NOW, null, 0));
        assertEquals(500, headway.seconds("428", "", "416", NOW));
        assertEquals(203, headway.seconds("426", "", "416", NOW));
    }

    @Test public void keepsTheScreenshotHeadwaySeparateFromTheArrivalCountdown() throws Exception {
        // Reconstructed from the 08:17:49 screenshot: 416 in Maragall via 2,
        // selected station La Pau, Hora 08:53 and Dist. 3:23.
        // The source screen only displays arrival minutes, so seconds are illustrative.
        JSONObject board = forecast("413", 4000, 2111);
        JSONObject position = new JSONObject().put("estacion", "Maragall").put("platformTrack", 2);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard("413", board, NOW);
        StationHeadway headway = new StationHeadway();
        headway.accept("413", board, NOW);
        ManualTrainEta manual = new ManualTrainEta("413", "416", false);
        assertFalse(manual.observe(position));
        assertEquals(NOW + 2_111_000L, manual.predictedArrival(boards, NOW, position, 0));
        assertEquals("3′23″", headway.label("413", "", "416", NOW));
        assertEquals(203, headway.seconds("413", "", "416", NOW + 7000));
    }

    @Test public void rejectsTheWrongBoardInsteadOfUsingTheCurrentLocationTimer() throws Exception {
        JSONObject otherStation = forecast("431", 4000, 2111);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard("413", otherStation, NOW);
        StationHeadway headway = new StationHeadway();
        headway.accept("413", otherStation, NOW);
        assertEquals(0, new ManualTrainEta("413", "416", false).predictedArrival(boards, NOW, null, 0));
        assertEquals(-1, headway.seconds("413", "", "416", NOW));
    }

    private static JSONObject forecast(String target, int first, int second) throws Exception {
        return new JSONObject().put("line", "L4").put("stopCode", target)
            .put("via1", new JSONArray().put(row("P16", first, 500)))
            .put("via2", new JSONArray().put(row("M16", second, 203)))
            .put("lineTrains", new JSONArray().put(row("416", 37, 600)));
    }

    private static JSONObject row(String train, int seconds, int headway) throws Exception {
        return new JSONObject().put("trainCode", train).put("stopCode", 431)
            .put("stopName", "Maragall").put("secsLeft", seconds)
            .put("arrivalTime", NOW + seconds * 1000L).put("secPreviousTrain", headway);
    }
}
