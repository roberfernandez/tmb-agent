package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class StationHeadwayTest {
    private static final long NOW = 1_794_432_895_666L;
    private static JSONObject row(String train, Object interval, long arrival) throws Exception {
        return new JSONObject().put("trainCode", train).put("secPreviousTrain", interval)
            .put("arrivalTime", arrival).put("secsLeft", 21);
    }
    private static JSONObject board(String stop, JSONArray first, JSONArray second) throws Exception {
        return new JSONObject().put("line", "L4").put("stopCode", stop)
            .put("via1", first).put("via2", second);
    }

    @Test public void readsThePublicForecastFieldFromTheRealLaPauCapture() throws Exception {
        JSONObject capture;
        try (InputStream input = getClass().getResourceAsStream("/miralin-la-pau-m06-2026-11-11.json")) {
            assertNotNull(input);
            capture = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        StationHeadway headway = new StationHeadway();
        headway.accept("413", capture.getJSONObject("board").getJSONObject("raw"), NOW);
        assertEquals(1269, headway.seconds("413", "", "P13", NOW));
        assertEquals("21′09″", headway.label("413", "", "413", NOW));
        assertEquals(37, headway.seconds("413", "", "409", NOW));
        assertEquals(-1, headway.seconds("413", "", "406", NOW));
        assertEquals("miralin.cat stopDataUpdate.413.via1", headway.source("413", "", "M13", NOW));
    }

    @Test public void readsTheStationRowsInsteadOfTheConflictingPositionInterval() throws Exception {
        StationHeadway headway = new StationHeadway();
        JSONObject data = board("413", new JSONArray().put(row("M11", 1946, NOW + 1000)), new JSONArray())
            .put("lineTrains", new JSONArray().put(row("411", 600, NOW + 7000)));
        headway.accept("413", data, NOW);
        assertEquals("32′26″", headway.label("413", "", "P11", NOW));
        assertEquals(1946, headway.seconds("413", "", "411", NOW + 15_000));
        data.getJSONArray("via1").getJSONObject(0).put("secPreviousTrain", JSONObject.NULL);
        headway.accept("413", data, NOW + 16_000);
        assertEquals("--", headway.label("413", "", "411", NOW + 16_000));
        assertEquals(600, data.getJSONArray("lineTrains").getJSONObject(0).getInt("secPreviousTrain"));
    }

    @Test public void matchesThe416SampleWith37SecondsWithoutUsingDepartureAsAnInterval() throws Exception {
        StationHeadway headway = new StationHeadway();
        headway.accept("413", board("413", new JSONArray()
            .put(row("416", 37, NOW + 21_000).put("stopCode", 413).put("track", 1)
                .put("isWaitingDeparture", true)), new JSONArray()), NOW);
        assertEquals("0′37″", headway.label("413", "2", "M16", NOW));
        assertEquals("0′37″", headway.label("413", "", "P16", NOW + 20_000));
    }

    @Test public void keepsStationsSeparateAndRejectsAWrongDeclaredStopOrLine() throws Exception {
        StationHeadway headway = new StationHeadway();
        headway.accept("413", board("413", new JSONArray().put(row("411", 600, NOW)), new JSONArray()), NOW);
        headway.accept("425", board("425", new JSONArray().put(row("411", 1200, NOW)), new JSONArray()), NOW);
        assertEquals(600, headway.seconds("413", "", "411", NOW));
        assertEquals(1200, headway.seconds("425", "", "411", NOW));
        headway.accept("413", board("425", new JSONArray().put(row("411", 999, NOW)), new JSONArray()), NOW + 1);
        headway.accept("413", board("413", new JSONArray().put(row("411", 999, NOW)), new JSONArray())
            .put("line", "L3"), NOW + 2);
        assertEquals(600, headway.seconds("413", "", "411", NOW + 2));
    }

    @Test public void honoursTheForecastSideAndManualModeChoosesTheNextOccurrence() throws Exception {
        StationHeadway headway = new StationHeadway();
        headway.accept("425", new JSONObject().put("data", board("425",
            new JSONArray().put(row("M11", 100, NOW + 60_000)),
            new JSONArray().put(row("P11", 200, NOW + 30_000)))), NOW);
        assertEquals(100, headway.seconds("425", "1", "411", NOW));
        assertEquals(200, headway.seconds("425", "2", "411", NOW));
        assertEquals(200, headway.seconds("425", "", "411", NOW));
    }

    @Test public void expiresWithoutBorrowingAnotherTrainsIntervalOrRefreshingItFromOldData() throws Exception {
        StationHeadway headway = new StationHeadway();
        JSONObject data = board("413", new JSONArray().put(row("416", 37, NOW)), new JSONArray());
        headway.accept("413", data, NOW);
        headway.accept("413", board("413", new JSONArray().put(row("416", 99, NOW)), new JSONArray()), NOW - 1);
        assertEquals(37, headway.seconds("413", "", "416", NOW));
        assertEquals(-1, headway.seconds("413", "", "416", NOW - 1));
        assertEquals(-1, headway.seconds("413", "", "416", NOW + 60_000));
        assertEquals(-1, headway.seconds("413", "", "407", NOW));
        assertTrue(headway.source("413", "", "416", NOW + 60_000).startsWith("stale: "));
        headway.clear();
        assertEquals(-1, headway.seconds("413", "", "416", NOW));
    }

    @Test public void preservesUnknownAndZeroAndRejectsInvalidIntervals() throws Exception {
        StationHeadway headway = new StationHeadway();
        for (Object invalid : new Object[]{JSONObject.NULL, -1, "not-a-time", "Infinity"}) {
            headway.accept("413", board("413", new JSONArray().put(row("416", invalid, NOW)), new JSONArray()), NOW);
            assertEquals("--", headway.label("413", "", "416", NOW));
        }
        headway.accept("413", board("413", new JSONArray().put(row("416", 0, NOW)), new JSONArray()), NOW);
        assertEquals("0′00″", headway.label("413", "", "416", NOW));
    }
}
