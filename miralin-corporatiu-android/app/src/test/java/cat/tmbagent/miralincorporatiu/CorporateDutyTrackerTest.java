package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public final class CorporateDutyTrackerTest {
    private static final String SCHEDULE =
        "1126,1146,TRA,413,422,,,;" +
        "1146,1226,EST,422,422,,,;" +
        "1226,1298,CON,422,434,414,2,RET;" +
        "1298,1409,EST,434,434,,,;" +
        "1409,1469,CON,434,413,405,2,RET;" +
        "1469,1490,EST,413,413,,,";
    private static long at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0,
            ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
    }
    private static CorporateDutyTracker tracker() {
        return new CorporateDutyTracker(new CorporateServicePlan("2026-11-17", SCHEDULE));
    }

    @Test public void doesNotChangeStationRowJustBecauseScheduledPickupHasPassed() {
        CorporateDutyTracker track = tracker();
        CorporateDutyTracker.Snapshot snapshot = track.at(at(17, 23, 41));
        assertNotNull(snapshot);
        assertTrue(snapshot.waiting);
        assertFalse(snapshot.driving);
        assertEquals(3, snapshot.photoRow); // 21:38-23:29, not row 4
        assertEquals(4, snapshot.pickupRow);
        assertEquals("405", snapshot.train);
        assertEquals("434", snapshot.from);
        assertEquals(434, snapshot.selection.stop);
        assertEquals(0, snapshot.selection.via);
        assertFalse(snapshot.selection.driving);
    }

    @Test public void viaJuliaIsNotAnArrivalAtTrinitatNova() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.observe(at(17,23,42), "405", "Via Júlia", "Via 1", true));
        assertFalse(track.at(at(17,23,43)).driving);
    }

    @Test public void twoReliableOfficialReadingsAtCorrectStationReleaseBoarding() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.observe(at(17,23,40), "405", "Trinitat Nova", "Via 1", true));
        assertTrue(track.observe(at(17,23,40)+10_000, "405", "Trinitat Nova", "Via 2", true));
        CorporateDutyTracker.Snapshot now = track.at(at(17,23,41));
        assertTrue(now.driving);
        assertFalse(now.waiting);
        assertEquals(4, now.photoRow);
        assertEquals(413, now.selection.stop); // Follow toward La Pau after confirmed pickup
        assertEquals("corporate", now.source);
    }

    @Test public void earlyPreviousLapMustNotTriggerTakeover() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.observe(at(17,22,0), "405", "Trinitat Nova", "Via 2", true));
        assertFalse(track.observe(at(17,22,0)+10_000, "405", "Trinitat Nova", "Via 2", true));
        assertFalse(track.at(at(17,23,31)).driving);
    }

    @Test public void ambiguousOrStaleObservationNeverConfirmsBoarding() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.observe(at(17,23,33), "405", "Trinitat Nova", "Via 2", true));
        assertFalse(track.observe(at(17,23,33)+10_000, "405", "Trinitat Nova", "Via 2", false));
        assertFalse(track.observe(at(17,23,34), "405", "Trinitat Nova", "Via 2", true));
        assertFalse(track.observe(at(17,23,34)+60_000, "405", "Trinitat Nova", "Via 2", true));
        assertFalse(track.at(at(17,23,35)).driving);
    }

    @Test public void manualConfirmationWorksWhenCorporatePositionIsAmbiguous() {
        CorporateDutyTracker track = tracker();
        assertTrue(track.confirmBoarded(at(17,23,47),4));
        CorporateDutyTracker.Snapshot snap = track.at(at(17,23,48));
        assertTrue(snap.driving);
        assertEquals("manual", snap.source);
        assertEquals(4, snap.photoRow);
    }

    @Test public void cannotConfirmWrongRowOrHoursBeforePickup() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.confirmBoarded(at(17,22,0),4));
        assertFalse(track.confirmBoarded(at(17,23,31),2));
        assertFalse(track.at(at(17,23,33)).driving);
    }

    @Test public void aLateLastPickupDoesNotExpireAtScheduledEnd() {
        CorporateDutyTracker track = tracker();
        CorporateDutyTracker.Snapshot snap = track.at(at(18,0,45));
        assertNotNull(snap);
        assertTrue(snap.waiting);
        assertEquals(3, snap.photoRow);
        assertNull(track.at(at(18,3,0)));
    }

    @Test public void onlyExactNormalizedStationMatches() {
        assertTrue(CorporateDutyTracker.sameStation("Trinitat Nova", "Trinitat Nova"));
        assertTrue(CorporateDutyTracker.sameStation("Via Júlia", "Via Julia"));
        assertFalse(CorporateDutyTracker.sameStation("Via Júlia", "Trinitat Nova"));
        assertFalse(CorporateDutyTracker.sameStation("A prop de Trinitat Nova", "Trinitat Nova"));
    }

    @Test public void stablePlanKeyCanBeComparedToWebScheduleString() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17",SCHEDULE);
        String expected = Integer.toHexString(("2026-11-17|"+SCHEDULE).hashCode());
        assertEquals(expected,plan.webKey());
    }
}
