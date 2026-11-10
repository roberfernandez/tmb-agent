package com.tmbagent.traineye;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import java.time.ZonedDateTime;
import java.util.Set;
import static org.junit.Assert.*;

public class PickupEtaTest {
    private static final String SERVICE = "1126,1150,EST,413,413,,,;1150,1169,TRA,413,425,,,;"
        + "1169,1285,EST,425,425,,,;1285,1385,CON,425,413,410,2,2;1385,1490,EST,413,413,,,";
    private static final long DAY = ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, ServicePlan.ZONE.toZoneId())
        .toInstant().toEpochMilli();

    @Test public void retainsTheRealPickupStationAfterScheduledDepartureUntilTrainArrives() throws JSONException {
        ServicePlan plan = new ServicePlan(SERVICE);
        PickupEta eta = new PickupEta(plan, time(19, 40), Set.of());
        assertNull(eta.target(time(18, 55), "410"));
        assertNull(eta.target(time(19, 20), "410"));
        assertNull(eta.target(time(21, 24), "411"));
        ServicePlan.Row pickup = eta.target(time(21, 24), "410");
        assertNotNull(pickup);
        assertEquals("425", pickup.from);
        assertEquals("2", pickup.via);
        assertFalse(eta.observe(pickup, position("Girona", 2), time(21, 26)));
        assertSame(pickup, eta.target(time(21, 27), "410"));
        assertTrue(eta.canShow(pickup, time(21, 27)));
        assertFalse(eta.observe(pickup, position("Passeig de Gràcia", 1), time(21, 27)));
        assertTrue(eta.observe(pickup, position("Passeig de Gràcia", 2), time(21, 27)));
        assertNull(eta.target(time(21, 28), "410"));
        assertNull(eta.target(time(22, 10), "410"));
        assertNull(eta.target(time(23, 10), "410"));
        assertNull(new PickupEta(plan, time(21, 28), eta.completedRows()).target(time(21, 28), "410"));
    }

    @Test public void keepsAnEstablishedWaitPastThirtyMinutesAndDetectsAJumpAcrossTheStation() throws JSONException {
        PickupEta eta = new PickupEta(new ServicePlan(SERVICE), time(21, 24), Set.of());
        ServicePlan.Row pickup = eta.target(time(21, 24), "410");
        assertSame(pickup, eta.target(time(22, 0), "410"));
        assertFalse(eta.observe(pickup, position("Verdaguer", 2), time(22, 0)));
        assertTrue(eta.observe(pickup, position("Urquinaona", 2), time(22, 3)));
        assertNull(eta.target(time(22, 4), "410"));
    }

    @Test public void coldStartDuringDrivingNeedsLiveApproachAndCanBeConfirmedManually() throws JSONException {
        PickupEta eta = new PickupEta(new ServicePlan(SERVICE), time(21, 26), Set.of());
        ServicePlan.Row pickup = eta.target(time(21, 26), "410");
        assertNotNull(pickup);
        assertFalse(eta.canShow(pickup, time(21, 26)));
        assertFalse(eta.observe(pickup, position("Jaume I", 2), time(21, 26)));
        assertFalse(eta.canShow(pickup, time(21, 26)));
        assertFalse(eta.observe(pickup, position("Girona", 2), time(21, 27)));
        assertTrue(eta.canShow(pickup, time(21, 27)));
        assertTrue(eta.confirm(pickup));
        assertNull(eta.target(time(21, 27), "410"));
    }

    @Test public void ignoresAnEarlyPassAndSameTrainContinuation() throws JSONException {
        ServicePlan plan = new ServicePlan(SERVICE.replace("1385,1490,EST,413,413,,,",
            "1385,1430,CON,413,434,410,1,1;1430,1490,EST,434,434,,,"));
        PickupEta eta = new PickupEta(plan, time(19, 40), Set.of());
        ServicePlan.Row pickup = eta.target(time(19, 40), "410");
        assertFalse(eta.observe(pickup, position("425", 2), time(20, 20)));
        assertSame(pickup, eta.target(time(21, 24), "410"));
        assertNull(eta.target(time(23, 5), "410"));
    }

    @Test public void recognisesArrivalAtTerminalWithTheOtherViaLabelAfterMidnight() throws JSONException {
        ServicePlan plan = new ServicePlan("1430,1450,EST,413,413,,,;1450,1490,CON,413,434,410,1,1");
        PickupEta eta = new PickupEta(plan, time(23, 58), Set.of());
        ServicePlan.Row pickup = eta.target(time(23, 58), "410");
        assertNotNull(pickup);
        assertSame(pickup, eta.target(time(24, 11), "410"));
        assertTrue(eta.observe(pickup, position("La Pau", 2), time(24, 12)));
        assertNull(eta.target(time(24, 12), "410"));
    }

    private static final String JOANIC = "812,857,EST,428,428,,,;857,917,CON,428,434,410,1,1";
    private static final long JOANIC_ARRIVAL = 1_794_315_801_000L;
    private static final long JOANIC_RECEIVED = JOANIC_ARRIVAL - 418_000L;

    @Test public void keepsTheRecordedJoanicArrivalThroughTheMissedRefresh() throws JSONException {
        ServicePlan plan = new ServicePlan(JOANIC);
        PickupEta eta = new PickupEta(plan, JOANIC_RECEIVED, Set.of());
        ServicePlan.Row pickup = eta.target(JOANIC_RECEIVED, "410");
        ServiceAlerts boards = new ServiceAlerts(plan, JOANIC_RECEIVED);
        boards.acceptBoard(joanicBoard(JOANIC_ARRIVAL), JOANIC_RECEIVED);

        long now = JOANIC_RECEIVED + 49_000L;
        assertEquals(369_000L, eta.predictedArrival(pickup, boards, now, null, 0L) - now);
        JSONObject approaching = position("Passeig de Gràcia", 1);
        assertEquals(JOANIC_ARRIVAL, eta.predictedArrival(pickup, boards, JOANIC_RECEIVED + 60_000L, approaching, 2_000L));
        assertEquals(JOANIC_ARRIVAL, eta.predictedArrival(pickup, boards, JOANIC_RECEIVED + 76_000L, approaching, 2_000L));
        assertEquals(0, boards.predictedArrival("428", "1", "410", JOANIC_RECEIVED + 76_000L));
        assertEquals(JOANIC_ARRIVAL, eta.predictedArrival(pickup, boards, JOANIC_RECEIVED + 119_999L, approaching, 2_000L));
        assertEquals(0, eta.predictedArrival(pickup, boards, JOANIC_RECEIVED + 120_000L, approaching, 2_000L));
    }

    @Test public void discardsTheHeldEtaWithoutARecentApproachOrAtArrival() throws JSONException {
        ServicePlan plan = new ServicePlan(JOANIC);
        PickupEta eta = new PickupEta(plan, JOANIC_RECEIVED, Set.of());
        ServicePlan.Row pickup = eta.target(JOANIC_RECEIVED, "410");
        ServiceAlerts boards = new ServiceAlerts(plan, JOANIC_RECEIVED);
        long arrival = JOANIC_RECEIVED + 85_000L;
        boards.acceptBoard(joanicBoard(arrival), JOANIC_RECEIVED);
        long now = JOANIC_RECEIVED + 76_000L;

        assertEquals(0, eta.predictedArrival(pickup, boards, now, null, 0));
        assertEquals(0, eta.predictedArrival(pickup, boards, now, position("Girona", 1), 15_000L));
        assertEquals(0, eta.predictedArrival(pickup, boards, now, position("Girona", 2), 2_000L));
        assertEquals(0, eta.predictedArrival(pickup, boards, now, position("Alfons X", 1), 2_000L));
        assertEquals(0, eta.predictedArrival(pickup, boards, now, position("Joanic", 1), 2_000L));
        assertEquals(0, eta.predictedArrival(pickup, boards, arrival, position("Girona", 1), 2_000L));
        assertTrue(eta.observe(pickup, position("Joanic", 1), now));
        assertEquals(0, eta.predictedArrival(pickup, boards, now, position("Girona", 1), 2_000L));
        assertNull(eta.target(now, "410"));
    }

    @Test public void replacesTheHeldEtaAsSoonAsMiralinSendsAnotherBoard() throws JSONException {
        ServicePlan plan = new ServicePlan(JOANIC);
        PickupEta eta = new PickupEta(plan, JOANIC_RECEIVED, Set.of());
        ServicePlan.Row pickup = eta.target(JOANIC_RECEIVED, "410");
        ServiceAlerts boards = new ServiceAlerts(plan, JOANIC_RECEIVED);
        boards.acceptBoard(joanicBoard(JOANIC_ARRIVAL), JOANIC_RECEIVED);
        long now = JOANIC_RECEIVED + 90_000L;
        JSONObject approach = position("Girona", 1);
        assertEquals(JOANIC_ARRIVAL, eta.predictedArrival(pickup, boards, now, approach, 2_000L));

        boards.acceptBoard(new JSONObject("{\"stopCode\":\"428\",\"via1\":[],"
            + "\"via2\":[{\"trainCode\":\"410\",\"secsLeft\":1600}]}"), now);
        assertEquals(0, eta.predictedArrival(pickup, boards, now, approach, 2_000L));

        long updated = JOANIC_ARRIVAL + 32_000L;
        boards.acceptBoard(joanicBoard(updated), now + 1000);
        assertEquals(updated, eta.predictedArrival(pickup, boards, now + 1000, null, 0L));
    }

    private static JSONObject joanicBoard(long arrival) throws JSONException {
        return new JSONObject("{\"line\":\"L4\",\"stopCode\":\"428\","
            + "\"via1\":[{\"trainCode\":\"410\",\"arrivalTime\":" + arrival + ",\"secsLeft\":418}],"
            + "\"via2\":[{\"trainCode\":\"410\",\"arrivalTime\":1794317302666,\"secsLeft\":1920}]}");
    }

    private static long time(int hour, int minute) { return DAY + (hour * 60L + minute) * 60_000; }

    private static JSONObject position(String station, int track) throws JSONException {
        return new JSONObject().put("estacion", station).put("platformTrack", track);
    }
}
