package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.*;

/** Replays the user's actual 0.14.32 diagnostic, not a synthetic station board. */
public class MiralinEtaCaptureTest {
    private static final long LA_PAU_ARRIVAL = 1_794_395_115_742L;

    private static JSONObject capture() throws Exception {
        try (InputStream input = MiralinEtaCaptureTest.class.getResourceAsStream(
                "/miralin-la-pau-m06-2026-11-11.json")) {
            assertNotNull("Missing captured diagnostic fixture", input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ServiceAlerts boards(JSONObject capture, ServicePlan plan) throws Exception {
        ServiceAlerts boards = new ServiceAlerts(plan, capture.getLong("capturedAt"));
        JSONObject board = capture.getJSONObject("board");
        boards.acceptBoard(capture.getString("requestedStop"), board.getJSONObject("raw"),
            board.getLong("receivedAt"));
        return boards;
    }

    @Test public void manual406AtAlfonsXUsesTheLaPauForecastInsteadOfThePositionTimer() throws Exception {
        JSONObject capture = capture();
        long now = capture.getLong("capturedAt");
        ServiceAlerts boards = boards(capture, new ServicePlan(""));
        ManualTrainEta eta = new ManualTrainEta("413", "406", false);
        JSONObject position = capture.getJSONObject("position");
        assertFalse(eta.observe(position));
        long arrival = eta.predictedArrival(boards, now, position, capture.getLong("positionAgeMs"));
        assertEquals(LA_PAU_ARRIVAL, arrival);
        assertEquals(3_116_707L, arrival - now);
        assertNotEquals(1_794_392_059_000L, arrival);
        assertEquals(LA_PAU_ARRIVAL, eta.predictedArrival(boards, now + 10_000, position, 10_072));
    }

    @Test public void manualTerminalDoesNotRequireTheInboundArrayToExist() throws Exception {
        JSONObject capture = capture();
        // Isolate the terminal-array bug from the separate current-stop validation bug.
        capture.getJSONObject("board").getJSONObject("raw").getJSONArray("via1")
            .getJSONObject(1).remove("stopCode");
        ServiceAlerts boards = boards(capture, new ServicePlan(""));
        ManualTrainEta eta = new ManualTrainEta("413", "P06", false);
        assertEquals(LA_PAU_ARRIVAL, eta.predictedArrival(boards, capture.getLong("capturedAt"),
            capture.getJSONObject("position"), capture.getLong("positionAgeMs")));
    }

    @Test public void servicePickupOnVia2AlsoUsesTheCapturedLaPauBoard() throws Exception {
        JSONObject capture = capture();
        long now = capture.getLong("capturedAt");
        ServicePlan plan = new ServicePlan(
            "600,725,EST,413,413,,,;725,800,CON,413,434,406,2,1");
        ServiceAlerts boards = boards(capture, plan);
        PickupEta eta = new PickupEta(plan, now, Collections.emptySet());
        ServicePlan.Row pickup = eta.target(now, "406");
        assertNotNull(pickup);
        PickupEta.Estimate estimate = eta.displayed(pickup, boards, now,
            capture.getJSONObject("position"), capture.getLong("positionAgeMs"));
        assertEquals(LA_PAU_ARRIVAL, estimate.arrival);
        assertFalse(estimate.approximate);
    }

    @Test public void missingStationForecastNeverFallsBackToThe41SecondPositionTimer() throws Exception {
        JSONObject capture = capture();
        JSONObject raw = capture.getJSONObject("board").getJSONObject("raw");
        raw.put("via1", new JSONArray().put(raw.getJSONArray("via1").getJSONObject(0)));
        ServiceAlerts boards = boards(capture, new ServicePlan(""));
        ManualTrainEta eta = new ManualTrainEta("413", "M06", false);
        long now = capture.getLong("capturedAt");
        assertEquals(0, eta.predictedArrival(boards, now, capture.getJSONObject("position"), 72));
        assertEquals(0, boards.predictedArrivalEitherTrack("413", "", "406", now));
    }
}
