package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class TrainHeadwayTest {
    private static final long NOW = 1_794_391_999_035L;

    private static JSONObject interval(Object value) throws Exception {
        return new JSONObject().put("secPreviousTrain", value);
    }

    @Test public void readsTheRealCaptureWithoutConfusingPositionOrArrivalTimers() throws Exception {
        JSONObject capture;
        try (InputStream input = getClass().getResourceAsStream("/miralin-la-pau-m06-2026-11-11.json")) {
            assertNotNull(input);
            capture = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        TrainHeadway headway = new TrainHeadway();
        headway.acceptBoard(capture.getJSONObject("board").getJSONObject("raw"), NOW);
        assertEquals(1269, headway.seconds("413", NOW));
        assertEquals("21′09″", headway.label("P13", NOW));
        assertEquals("0′37″", headway.label("409", NOW));
        // M06 has null T.ANT, even though its position timer and La Pau ETA exist.
        assertEquals(-1, headway.seconds("406", NOW));
        assertEquals("--", headway.label("M06", NOW));
    }

    @Test public void headwayIsAnIntervalAndDoesNotCountDownBetweenFrames() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        headway.acceptMap(new JSONObject().put("M06", interval(1131)), NOW);
        assertEquals("18′51″", headway.label("406", NOW));
        assertEquals("18′51″", headway.label("P06", NOW + 15_000));
        assertEquals("--", headway.label("406", NOW + TrainHeadway.FRESH_MS));
        assertEquals("--", headway.label("407", NOW));
    }

    @Test public void missingMapFieldsDoNotEraseARecentBoardIntervalButNullDoes() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        JSONObject board = new JSONObject().put("data", new JSONObject().put("via2",
            new JSONArray().put(interval(1131).put("trainCode", "M06"))));
        headway.acceptBoard(board, NOW);
        headway.acceptMap(new JSONObject().put("406", new JSONObject().put("secsLeft", 7)), NOW + 1000);
        assertEquals("18′51″", headway.label("406", NOW + 1000));
        headway.acceptMap(new JSONObject().put("P06", interval(JSONObject.NULL)), NOW + 2000);
        assertEquals("--", headway.label("406", NOW + 2000));
    }

    @Test public void explicitNullInTheCurrentTrainListWinsOverForecastCopies() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        JSONObject board = new JSONObject()
            .put("lineTrains", new JSONArray().put(interval(JSONObject.NULL).put("tren", "M06")))
            .put("via1", new JSONArray().put(interval(1131).put("trainCode", "406")));
        headway.acceptBoard(board, NOW);
        assertEquals("--", headway.label("406", NOW));
        board.put("lineTrains", new JSONArray().put(interval(0).put("tren", "P06")));
        headway.acceptBoard(board, NOW + 1000);
        assertEquals("0′00″", headway.label("406", NOW + 1000));
    }

    @Test public void rejectsInvalidValuesAndDoesNotReviveOlderMeasurements() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        headway.acceptMap(new JSONObject().put("406", interval(1131)), NOW);
        headway.acceptMap(new JSONObject().put("M06", interval(-1)), NOW + 1000);
        headway.acceptMap(new JSONObject().put("406", interval(44)), NOW);
        assertEquals("--", headway.label("406", NOW + 1000));
        headway.acceptMap(new JSONObject().put("406", interval("not-a-time")), NOW + 2000);
        assertEquals("--", headway.label("406", NOW + 2000));
        headway.acceptMap(new JSONObject().put("M06", interval(3723)), NOW + 3000);
        assertEquals("1h02", headway.label("406", NOW + 3000));
        headway.clear();
        assertEquals("--", headway.label("406", NOW + 3000));
    }

    @Test public void preservesTheHeadwayListsFromTheRealMapCapture() throws Exception {
        JSONObject line;
        try (InputStream input = getClass().getResourceAsStream("/miralin-l4-map-2026-11-09.json")) {
            assertNotNull(input);
            line = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertFalse(line.getJSONObject("trains").getJSONObject("416").has("secPreviousTrain"));
        JSONObject positions = TrainHeadway.positionsFromLine(line);
        assertEquals(12, positions.getJSONObject("416").getInt("secsLeft"));
        assertEquals(192, positions.getJSONObject("416").getInt("secPreviousTrain"));
        assertFalse(line.getJSONObject("trains").getJSONObject("416").has("secPreviousTrain"));
        TrainHeadway headway = new TrainHeadway();
        headway.acceptMap(positions, NOW);
        assertEquals("3′12″", headway.label("M16", NOW));
        assertEquals("mapUpdate.L4.trenesV1", headway.source("P16", NOW));
        assertEquals("--", headway.label("412", NOW));
    }

    @Test public void aRecentMapIntervalSurvivesANullStationBoardWithoutBecomingACountdown() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        JSONObject line = new JSONObject()
            .put("trains", new JSONObject().put("M16", new JSONObject()
                .put("estacion", "Jaume I").put("platformTrack", 2).put("secsLeft", 37)))
            .put("trenesV2", new JSONArray().put(interval(977).put("trainCode", "P16")));
        headway.acceptMap(TrainHeadway.positionsFromLine(line), NOW);
        JSONObject nullBoard = new JSONObject().put("lineTrains", new JSONArray()
            .put(interval(JSONObject.NULL).put("trainCode", "416").put("secsLeft", 41)));
        headway.acceptBoard(nullBoard, NOW + 2000);
        assertEquals("16′17″", headway.label("416", NOW + 2000));
        assertEquals("mapUpdate.L4.trenesV2", headway.source("416", NOW + 2000));
        headway.acceptBoard(nullBoard, NOW + 59_000);
        assertEquals("16′17″", headway.label("416", NOW + 59_000));
        assertEquals("--", headway.label("416", NOW + TrainHeadway.FRESH_MS));
    }

    @Test public void aNewMapNullInvalidatesTheOldIntervalAndAStaleMapAllowsBoardFallback() throws Exception {
        TrainHeadway headway = new TrainHeadway();
        headway.acceptMap(new JSONObject().put("416", interval(977)), NOW);
        headway.acceptMap(new JSONObject().put("M16", interval(JSONObject.NULL)), NOW + 1000);
        JSONObject board = new JSONObject().put("lineTrains",
            new JSONArray().put(interval(1000).put("trainCode", "416")));
        headway.acceptBoard(board, NOW + 2000);
        assertEquals("--", headway.label("416", NOW + 2000));
        assertEquals("mapUpdate.L4.trains", headway.source("416", NOW + 2000));
        headway.acceptBoard(board, NOW + 1000 + TrainHeadway.FRESH_MS);
        assertEquals("16′40″", headway.label("416", NOW + 1000 + TrainHeadway.FRESH_MS));
        assertEquals("stopDataUpdate.lineTrains",
            headway.source("416", NOW + 1000 + TrainHeadway.FRESH_MS));
    }

}
