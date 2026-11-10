package com.tmbagent.traineye;

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

    @Test public void retainsTheRealPickupStationAfterScheduledDepartureUntilTrainArrives() {
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

    @Test public void keepsAnEstablishedWaitPastThirtyMinutesAndDetectsAJumpAcrossTheStation() {
        PickupEta eta = new PickupEta(new ServicePlan(SERVICE), time(21, 24), Set.of());
        ServicePlan.Row pickup = eta.target(time(21, 24), "410");
        assertSame(pickup, eta.target(time(22, 0), "410"));
        assertFalse(eta.observe(pickup, position("Verdaguer", 2), time(22, 0)));
        assertTrue(eta.observe(pickup, position("Urquinaona", 2), time(22, 3)));
        assertNull(eta.target(time(22, 4), "410"));
    }

    @Test public void coldStartDuringDrivingNeedsLiveApproachAndCanBeConfirmedManually() {
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

    @Test public void ignoresAnEarlyPassAndSameTrainContinuation() {
        ServicePlan plan = new ServicePlan(SERVICE.replace("1385,1490,EST,413,413,,,",
            "1385,1430,CON,413,434,410,1,1;1430,1490,EST,434,434,,,"));
        PickupEta eta = new PickupEta(plan, time(19, 40), Set.of());
        ServicePlan.Row pickup = eta.target(time(19, 40), "410");
        assertFalse(eta.observe(pickup, position("425", 2), time(20, 20)));
        assertSame(pickup, eta.target(time(21, 24), "410"));
        assertNull(eta.target(time(23, 5), "410"));
    }

    @Test public void recognisesArrivalAtTerminalWithTheOtherViaLabelAfterMidnight() {
        ServicePlan plan = new ServicePlan("1430,1450,EST,413,413,,,;1450,1490,CON,413,434,410,1,1");
        PickupEta eta = new PickupEta(plan, time(23, 58), Set.of());
        ServicePlan.Row pickup = eta.target(time(23, 58), "410");
        assertNotNull(pickup);
        assertSame(pickup, eta.target(time(24, 11), "410"));
        assertTrue(eta.observe(pickup, position("La Pau", 2), time(24, 12)));
        assertNull(eta.target(time(24, 12), "410"));
    }

    private static long time(int hour, int minute) { return DAY + (hour * 60L + minute) * 60_000; }

    private static JSONObject position(String station, int track) {
        return new JSONObject().put("estacion", station).put("platformTrack", track);
    }
}
