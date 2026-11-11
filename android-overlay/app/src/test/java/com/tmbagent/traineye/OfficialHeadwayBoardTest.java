package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class OfficialHeadwayBoardTest {
    private static final long NOW = ServicePlanTest.time("2026-11-11T21:52:20");

    private static JSONObject capture() throws Exception {
        try (InputStream in = OfficialHeadwayBoardTest.class.getResourceAsStream("/official-tmb-la-pau-2026-11-11.json")) {
            assertNotNull(in);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void readsTheRealOfficialTempsColumnSeparatelyFromArrivalHour() throws Exception {
        OfficialHeadwayBoard board = OfficialHeadwayBoard.parse(capture(), "413", NOW);
        assertEquals(1946, board.seconds("411", "", NOW));
        assertEquals("32′26″", OfficialHeadwayBoard.format(board.seconds("411", "", NOW)));
        assertEquals(2117, board.seconds("P01", "1", NOW));
        assertEquals(-1, board.seconds("416", "", NOW));
    }

    @Test public void rejectsWrongStopLineOldServerClockAndExpiredSnapshots() throws Exception {
        try { OfficialHeadwayBoard.parse(capture(), "425", NOW); fail(); }
        catch (IllegalArgumentException expected) {}
        try { OfficialHeadwayBoard.parse(capture().put("line", "3"), "413", NOW); fail(); }
        catch (IllegalArgumentException expected) {}
        try { OfficialHeadwayBoard.parse(capture(), "413", NOW + 180_000); fail(); }
        catch (IllegalArgumentException expected) {}
        OfficialHeadwayBoard board = OfficialHeadwayBoard.parse(capture(), "413", NOW);
        assertEquals(-1, board.seconds("411", "", NOW + OfficialHeadwayBoard.FRESH_MS));
        assertEquals(-1, board.seconds("411", "", NOW - 1));
    }

    @Test public void preservesUnknownIntervalsAndHonoursTrackAtIntermediateStations() throws Exception {
        JSONObject snapshot = capture().put("stop", "425").put("tables", new JSONArray()
            .put(new JSONObject().put("via", "1").put("html",
                "<tr><td>M11</td><td>22:30</td><td>10:00</td><td>Girona</td><td>Via 1</td></tr>"))
            .put(new JSONObject().put("via", "2").put("html",
                "<tr><td>P11</td><td>21:55</td><td>&nbsp;</td><td>Girona</td><td>Via 2</td></tr>")));
        OfficialHeadwayBoard board = OfficialHeadwayBoard.parse(snapshot, "425", NOW);
        assertEquals(600, board.seconds("411", "1", NOW));
        assertEquals(-1, board.seconds("411", "2", NOW));
        assertEquals(-1, board.seconds("411", "", NOW));
        assertEquals(-1, OfficialHeadwayBoard.interval("32:99"));
        assertEquals(-1, OfficialHeadwayBoard.interval("--"));
        assertEquals(0, OfficialHeadwayBoard.interval("00:00"));
        assertEquals(3723, OfficialHeadwayBoard.interval("1:02:03"));
    }

    @Test public void permitsOnlyTheOfficialHttpsOriginAndMiralinPaths() {
        assertTrue(OfficialHeadwayBoard.allowsUrl("https://aplicacions.tmb.cat/miralin/routes/4/stops/413?dswid=123"));
        assertTrue(OfficialHeadwayBoard.allowsUrl("https://aplicacions.tmb.cat/miralin/javax.faces.resource/core.js.xhtml"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("http://aplicacions.tmb.cat/miralin/"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("https://aplicacions.tmb.cat.evil.test/miralin/"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("https://user:pass@aplicacions.tmb.cat/miralin/"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("https://aplicacions.tmb.cat/miralin/../other"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("file:///miralin/"));
        assertFalse(OfficialHeadwayBoard.allowsUrl("javascript:alert(1)"));
    }
}
