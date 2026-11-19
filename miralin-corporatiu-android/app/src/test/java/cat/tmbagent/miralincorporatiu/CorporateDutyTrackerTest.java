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

    @Test public void selectingStationRowKeepsAutomaticTrainMonitoringAndAwaitingArrival() {
        CorporateDutyTracker track = tracker();
        long now = at(17,23,41);
        assertTrue(track.selectRow(now, 3));
        CorporateDutyTracker.Snapshot selected = track.at(now);
        assertEquals(3, selected.photoRow);
        assertEquals(4, selected.pickupRow);
        assertEquals("405", selected.train);
        assertFalse(selected.driving);
        assertEquals(434, selected.selection.stop);
        assertEquals("manual-row", selected.source);
        assertFalse(track.observe(now+10_000, "405", "Via Júlia", "Via 1", true));
        assertFalse(track.at(now+11_000).driving);
        assertFalse(track.observe(now+20_000, "405", "Trinitat Nova", "Via 2", true));
        assertTrue(track.observe(now+31_000, "405", "Trinitat Nova", "Via 2", true));
        CorporateDutyTracker.Snapshot aboard = track.at(now+32_000);
        assertTrue(aboard.driving);
        assertEquals(4, aboard.photoRow);
        assertEquals(413, aboard.selection.stop);
        assertEquals("corporate", aboard.source);
    }

    @Test public void tappingDrivingRowMarksPickupWithoutStoppingAutomaticTracking() {
        CorporateDutyTracker track = tracker();
        long now = at(17,23,42);
        assertTrue(track.selectRow(now, 4));
        CorporateDutyTracker.Snapshot aboard = track.at(now);
        assertTrue(aboard.driving);
        assertEquals(4, aboard.photoRow);
        assertEquals("405", aboard.train);
        assertEquals(413, aboard.selection.stop);
        assertEquals("manual-row", aboard.source);
        assertEquals(5, track.at(at(18,0,30)).photoRow);
    }

    @Test public void manualRowMayReturnToScheduleButNeverAutoBoardOnlyAtClockTime() {
        CorporateDutyTracker track = tracker();
        assertTrue(track.selectRow(at(17,23,0), 3));
        CorporateDutyTracker.Snapshot afterPickupTime = track.at(at(17,23,35));
        assertEquals(3, afterPickupTime.photoRow);
        assertTrue(afterPickupTime.waiting);
        assertEquals("405", afterPickupTime.train);
        assertFalse(afterPickupTime.driving);
    }

    @Test public void aFreshStartDuringTheLastDrivingRowDoesNotAssumeEarlierTrainsWereMissed() {
        CorporateDutyTracker track = tracker();
        CorporateDutyTracker.Snapshot snapshot = track.at(at(17,23,41));
        assertEquals("405", snapshot.train);
        assertEquals(3, snapshot.photoRow);
        assertTrue(snapshot.waiting);
    }

    @Test public void anAlreadyWatchedLatePickupStaysOnItsOriginalStationPastScheduledEnd() {
        CorporateDutyTracker track = tracker();
        CorporateDutyTracker.Snapshot initially = track.at(at(17,20,21));
        assertEquals("414", initially.train);
        assertEquals("422", initially.from);
        CorporateDutyTracker.Snapshot late = track.at(at(17,21,45));
        assertEquals("414", late.train);
        assertEquals(1, late.photoRow);
        assertTrue(late.waiting);
        assertEquals(422, late.selection.stop);
        // The worker can correct their stage with a single row tap without
        // disabling the eye or changing the original timetable.
        assertTrue(track.selectRow(at(17,21,46), 3));
        CorporateDutyTracker.Snapshot corrected = track.at(at(17,21,46));
        assertEquals("405", corrected.train);
        assertEquals(3, corrected.photoRow);
        assertEquals(434, corrected.selection.stop);
    }

    @Test public void selectingAnOutOfHoursRowAutomaticallyBecomesPreviewNotRealDuty() {
        CorporateDutyTracker track = tracker();
        assertFalse(track.selectRow(at(17,20,0), -1));
        assertFalse(track.selectRow(at(17,20,0), 250));
        assertTrue(track.selectRow(at(17,20,0), 4));
        assertTrue(track.isPreviewOnly());
        CorporateDutyTracker.Snapshot snap = track.at(at(17,20,0));
        assertTrue(snap.preview);
        assertEquals("preview", snap.source);
        assertEquals(4, snap.photoRow);
        assertFalse(track.observe(at(17,23,40), "405", "Trinitat Nova", "Via 2", true));
    }

    @Test public void outOfHoursPreviewAcceptsAnExpiredShiftButDoesNotFakeBoarding() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17", SCHEDULE);
        long now = at(18, 10, 0);
        assertNull(new CorporateDutyTracker(plan).at(now));
        CorporateDutyTracker preview = new CorporateDutyTracker(plan, true);
        CorporateDutyTracker.Snapshot snapshot = preview.at(now);
        assertNotNull(snapshot);
        assertTrue(snapshot.preview);
        assertEquals("preview", snapshot.source);
        assertEquals("405", snapshot.train);
        assertFalse(snapshot.driving);
        assertFalse(preview.observe(now, "405", "Trinitat Nova", "Via 2", true));
        assertFalse(preview.observe(now + 10_000, "405", "Trinitat Nova", "Via 2", true));
        assertFalse(preview.at(now + 11_000).driving);
    }

    @Test public void previewAllowsAnyRowToBeSelectedWithoutStoppingCorporateTrainPolling() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17", SCHEDULE);
        CorporateDutyTracker preview = new CorporateDutyTracker(plan, true);
        long now = at(18, 10, 0);
        assertTrue(preview.selectRow(now, 1));
        assertEquals(1, preview.at(now).photoRow);
        assertTrue(preview.selectRow(now, 4));
        assertEquals(4, preview.at(now).photoRow);
        assertTrue(preview.at(now).preview);
    }

    @Test public void beforeTransferStartShowsOnlyCountdownAndDoesNotFollowPassingTrains() {
        CorporateDutyTracker track = tracker();
        CorporateDutyTracker.Snapshot snap = track.at(at(17,18,30));
        assertNotNull(snap);
        assertEquals(CorporateServicePlan.Selection.IDLE, snap.selection.kind);
        assertEquals("", snap.selection.train);
        assertEquals(413, snap.selection.stop);
        assertEquals(at(17,18,46), snap.selection.takeoverAt);
    }

    @Test public void transferFindsAnyTrainInTheRequiredDirectionAndTracksItToDestination() {
        CorporateDutyTracker track = tracker();
        long start = at(17,18,46);
        CorporateDutyTracker.Snapshot searching = track.at(start);
        assertEquals(CorporateServicePlan.Selection.TRANSFER_SEARCH, searching.selection.kind);
        assertEquals(413, searching.selection.origin);
        assertEquals(422, searching.selection.destination);
        assertEquals(1, searching.selection.via);
        assertTrue(track.setTransferTrain(0, "417"));
        CorporateDutyTracker.Snapshot tracking = track.at(start + 1_000);
        assertEquals(CorporateServicePlan.Selection.TRANSFER_TRACK, tracking.selection.kind);
        assertEquals("417", tracking.selection.train);
        assertEquals(422, tracking.selection.stop);
        assertEquals(1, tracking.selection.via);
        assertFalse(track.observeTransfer(at(17,19,10), "417", "Via Júlia"));
        assertTrue(track.observeTransfer(at(17,19,10)+10_000, "417", "Trinitat Nova"));
        CorporateDutyTracker.Snapshot atStation = track.at(at(17,19,10)+11_000);
        assertEquals(CorporateServicePlan.Selection.PICKUP, atStation.selection.kind);
        assertEquals("414", atStation.train);
        assertEquals(422, atStation.selection.stop);
    }

    @Test public void earlyTransferArrivalWaitsAtDestinationUntilStationDutyStarts() {
        CorporateDutyTracker track = tracker();
        long start = at(17,18,46);
        track.at(start);
        assertTrue(track.setTransferTrain(0, "417"));
        assertFalse(track.observeTransfer(start+60_000, "417", "Trinitat Nova"));
        assertTrue(track.observeTransfer(start+70_000, "417", "Trinitat Nova"));
        CorporateDutyTracker.Snapshot waitingAtDestination = track.at(start+71_000);
        assertEquals(CorporateServicePlan.Selection.IDLE, waitingAtDestination.selection.kind);
        assertEquals(422, waitingAtDestination.selection.stop);
        assertEquals(at(17,19,06), waitingAtDestination.selection.takeoverAt);
        CorporateDutyTracker.Snapshot stationDuty = track.at(at(17,19,06));
        assertEquals(CorporateServicePlan.Selection.PICKUP, stationDuty.selection.kind);
        assertEquals("414", stationDuty.train);
    }

    @Test public void delayedTransferContinuesAfterItsScheduledEndUntilArrivalIsConfirmed() {
        CorporateDutyTracker track = tracker();
        long start = at(17,18,46);
        track.at(start);
        assertTrue(track.setTransferTrain(0, "417"));
        CorporateDutyTracker.Snapshot late = track.at(at(17,19,10));
        assertEquals(CorporateServicePlan.Selection.TRANSFER_TRACK, late.selection.kind);
        assertEquals("417", late.selection.train);
        assertFalse(track.observeTransfer(at(17,19,10), "417", "Via Júlia"));
        assertTrue(track.observeTransfer(at(17,19,10)+10_000, "417", "Trinitat Nova"));
        assertEquals(CorporateServicePlan.Selection.PICKUP,
            track.at(at(17,19,10)+11_000).selection.kind);
    }

    @Test public void anOverlappingDrivingPieceHasPriorityOverStationPresence() {
        String overlap = "1126,1146,TRA,413,422,,,;" +
            "1146,1298,EST,422,422,,,;" +
            "1226,1298,CON,422,434,414,2,RET;" +
            "1298,1409,EST,434,434,,,;" +
            "1409,1469,CON,434,413,405,2,RET;" +
            "1469,1490,EST,413,413,,,";
        CorporateDutyTracker track = new CorporateDutyTracker(
            new CorporateServicePlan("2026-11-17", overlap));
        CorporateDutyTracker.Snapshot snap = track.at(at(17,20,30));
        assertEquals(CorporateServicePlan.Selection.PICKUP, snap.selection.kind);
        assertEquals("414", snap.train);
        assertEquals(2, snap.pickupRow);
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
