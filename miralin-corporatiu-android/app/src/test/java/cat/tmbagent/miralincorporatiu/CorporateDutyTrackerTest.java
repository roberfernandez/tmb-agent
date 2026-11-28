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

    @Test public void startingMidShiftUsesCurrentPieceWithoutReplayingEarlierArrivals() {
        String schedule = "1126,1154,EST,413,413,,,;1154,1164,TRA,413,417,,,;"
            + "1164,1320,CON,417,413,413,1,2;1320,1490,EST,413,413,,,";
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-25", schedule);
        int[][] cases = {{25,18,31,0},{25,18,55,0},{25,19,14,1},{25,19,24,2},
            {25,19,40,2},{25,22,0,3},{26,0,35,3}};
        for (int[] sample : cases) {
            long now = at(sample[0], sample[1], sample[2]);
            CorporateDutyTracker tracker = new CorporateDutyTracker(plan);
            tracker.resumeAt(now);
            CorporateDutyTracker.Snapshot state = tracker.at(now);
            assertEquals(sample[3], state.photoRow);
            assertEquals(sample[3] == 2, state.driving);
            if (state.driving) assertEquals("413", state.train);
        }
    }

    @Test public void followsTerminalDepartureAndActualSelvaPickupEarlyOrLate() {
        String schedule = "1126,1154,EST,413,413,,,;1154,1164,TRA,413,417,,,;"
            + "1164,1320,CON,417,413,413,1,2;1320,1490,EST,413,413,,,";
        for (int offset : new int[] {-5, 8}) {
            CorporateDutyTracker t = new CorporateDutyTracker(new CorporateServicePlan("2026-11-25", schedule));
            long departure = at(25,19,14) + offset * 60_000L;
            long pickup = departure + 10 * 60_000L;
            assertTrue(t.observe(departure, "413", "La Pau", "Via 2", true, departure));
            assertEquals(1, t.at(departure).photoRow);
            assertFalse(t.observe(departure + 60_000, "413", "Besòs", "Via 1", true));
            assertEquals(1, t.at(pickup).photoRow);
            assertFalse(t.observe(pickup, "412", "Selva de Mar", "Via 1", true));
            assertFalse(t.observe(pickup, "413", "Selva de Mar", "Via 2", true));
            assertTrue(t.observe(pickup, "413", "Selva de Mar", "Via 1", true));
            assertEquals(2, t.at(pickup).photoRow);
        }
    }

    @Test public void sameTrainLeavesStationOnItsActualPassageAndTakesOverAtTerminal() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-22",
            "1146,1286,EST,424,424,,,;1286,1310,TRA,424,413,,,;1310,1485,CON,413,413,405,2,RET");
        for (int minute : new int[] {23, 29, 48}) {
            CorporateDutyTracker tracker = new CorporateDutyTracker(plan);
            long passage = at(22, 21, minute);
            long previousLap = passage - CorporateProjection.LAP_MS;
            assertEquals("405", tracker.at(previousLap).train);
            assertFalse(tracker.observe(previousLap, "405", "Jaume I", "Via 2", true));
            assertFalse(tracker.observe(previousLap, "405", "Urquinaona", "Via 2", true, previousLap));
            assertFalse(tracker.observe(previousLap + 10_000, "405", "Jaume I", "Via 2", true, passage));
            assertFalse(tracker.observe(at(22,20,52), "405", "Poblenou", "Via 2", true, passage));
            assertEquals(0, tracker.at(at(22,20,52)).photoRow);
            assertFalse(tracker.observe(passage, "404", "Jaume I", "Via 2", true));
            assertFalse(tracker.observe(passage, "405", "Jaume I", "Via 1", true));
            assertTrue(tracker.observe(passage, "405", "Urquinaona", "Via 2", true, passage));
            assertEquals(1, tracker.at(passage).photoRow);
            assertEquals(413, tracker.at(passage).selection.stop);
            assertFalse(tracker.observe(passage + 10_000, "405", "Jaume I", "Via 2", true));
            assertEquals(1, tracker.at(passage + 11_000).photoRow);
            assertEquals(413, tracker.at(passage + 11_000).selection.stop);
            long takeover = at(22, 21, 52);
            assertFalse(tracker.observe(takeover, "405", "La Pau", "Via 2", true));
            assertTrue(tracker.observe(takeover + 10_000, "405", "La Pau", "Via 2", true));
            assertTrue(tracker.at(takeover + 11_000).driving);
            assertEquals(2, tracker.at(takeover + 11_000).photoRow);
        }
        assertTrue(plan.noticesAt(at(22, 21, 25), 5).isEmpty());
        assertTrue(plan.noticesAt(at(22, 21, 49), 5).isEmpty());
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
        track.at(at(17,23,30)); // This instance was already watching the delayed pickup.
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
        long delayed = at(18,0,40);
        assertEquals(4, track.at(delayed).photoRow);
        assertTrue(track.at(delayed).driving);
        assertFalse(track.observe(delayed, "405", "La Pau", "Via 2", true));
        assertTrue(track.observe(delayed + 10_000, "405", "La Pau", "Via 2", true));
        assertEquals(5, track.at(delayed + 11_000).photoRow);
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

    @Test public void delayedPickupAllowsSelectingThePreviousStationAfterScheduledStart() {
        CorporateDutyTracker track = tracker();
        long delayed = at(17,23,40); // CON row 4 is already due, train not boarded.
        assertTrue(track.selectRow(delayed, 3)); // EST immediately before that CON.
        CorporateDutyTracker.Snapshot selected = track.at(delayed);
        assertEquals(3, selected.photoRow);
        assertEquals(4, selected.pickupRow);
        assertTrue(selected.waiting);
        assertFalse(selected.driving);
    }

    @Test public void manualPreviousPieceOverridesAClockOnlyResume() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17", SCHEDULE);
        CorporateDutyTracker track = new CorporateDutyTracker(plan);
        long now = at(17,21,50); // inside the first CON row
        track.resumeAt(now); // position seed must not count as boarding evidence
        assertTrue(track.selectRow(now, 1)); // return to the preceding EST row
        CorporateDutyTracker.Snapshot selected = track.at(now);
        assertEquals(1, selected.photoRow);
        assertFalse(selected.driving);
        assertTrue(selected.waiting);
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
        long now = at(18, 2, 30);
        assertNull(new CorporateDutyTracker(plan).at(now));
        CorporateDutyTracker preview = new CorporateDutyTracker(plan, true);
        CorporateDutyTracker.Snapshot snapshot = preview.at(now);
        assertNotNull(snapshot);
        assertTrue(snapshot.preview);
        assertEquals("preview", snapshot.source);
        assertEquals("405", snapshot.train);
        assertTrue(snapshot.waiting);
        assertFalse(preview.observe(now, "414", "Trinitat Nova", "Via 2", true));
        assertFalse(preview.observe(now + 10_000, "414", "Trinitat Nova", "Via 2", true));
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

    @Test public void transferShowsScheduledDepartureAndDirectionBeforeAndDuringMovement() {
        CorporateDutyTracker track = tracker();
        long start = at(17,18,46);
        for (long now : new long[]{at(17,18,30), start, at(17,19,0)}) {
            CorporateServicePlan.Selection selected = track.at(now).selection;
            assertEquals(CorporateServicePlan.Selection.TRANSFER, selected.kind);
            assertFalse(selected.watchesTrain());
            assertEquals("", selected.train);
            assertEquals(413, selected.stop);
            assertEquals(1, selected.via);
            assertEquals(start, selected.takeoverAt);
        }
        assertEquals(CorporateServicePlan.Selection.PICKUP, track.at(at(17,19,6)).selection.kind);
    }

    @Test public void selectingReverseTransferReleasesDelayedTrainAndShowsViaTwo() {
        String schedule = "1126,1146,CON,413,422,420,1,1;"
            + "1146,1166,TRA,422,413,,,;1166,1190,CON,413,434,405,1,RET";
        CorporateDutyTracker track = new CorporateDutyTracker(new CorporateServicePlan("2026-11-17", schedule));
        assertTrue(track.selectRow(at(17,18,55), 0));
        assertEquals("420", track.at(at(17,19,8)).train);
        assertTrue(track.selectRow(at(17,19,8), 1));
        CorporateServicePlan.Selection transfer = track.at(at(17,19,8)).selection;
        assertFalse(transfer.watchesTrain());
        assertEquals("", transfer.train);
        assertEquals(2, transfer.via);
        assertEquals(at(17,19,6), transfer.takeoverAt);
        assertEquals("405", track.at(at(17,19,26)).train);
    }

    @Test public void scheduledPickupDoesNotReplaceAnOngoingTransfer() {
        String schedule = "1126,1146,TRA,413,422,,,;1144,1190,CON,422,434,420,1,RET";
        CorporateDutyTracker track = new CorporateDutyTracker(new CorporateServicePlan("2026-11-17", schedule));
        assertFalse(track.at(at(17,19,5)).selection.watchesTrain());
        assertEquals("420", track.at(at(17,19,6)).train);
    }

    @Test public void passingTheDestinationOnAnEarlierLapDoesNotEndManualDriving() {
        String schedule = "1080,1200,CON,413,413,420,1,RET;1200,1220,EST,413,413,,,";
        CorporateDutyTracker track = new CorporateDutyTracker(new CorporateServicePlan("2026-11-17", schedule));
        long earlierLap = at(17,18,43);
        assertTrue(track.selectRow(at(17,18,0), 0));
        assertFalse(track.observe(earlierLap, "420", "La Pau", "Via 2", true));
        assertFalse(track.observe(earlierLap + 10_000, "420", "La Pau", "Via 2", true));
        assertTrue(track.at(earlierLap + 11_000).driving);
    }

    @Test public void lateCheckpointCanReopenDrivingEvenAfterALaterBoardingWasRecorded() {
        CorporateDutyTracker track = tracker();
        assertTrue(track.selectRow(at(17,23,30), 4));
        long delayed = at(17,23,42);
        assertTrue(track.selectRow(delayed, 2));
        CorporateDutyTracker.Snapshot aboard = track.at(delayed);
        assertTrue(aboard.driving);
        assertFalse(aboard.preview);
        assertEquals("414", aboard.train);
        assertEquals(2, aboard.photoRow);
        assertEquals(at(17,21,38), aboard.selection.takeoverAt);
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

    @Test public void drivingRemainsActivePastScheduledEndUntilTrainIsConfirmedAtDestination() {
        CorporateDutyTracker track = tracker();
        long pickup = at(17,23,40);
        assertFalse(track.observe(pickup, "405", "Trinitat Nova", "Via 1", true));
        assertTrue(track.observe(pickup+10_000, "405", "Trinitat Nova", "Via 1", true));
        CorporateDutyTracker.Snapshot stillDriving = track.at(at(18,0,35));
        assertTrue(stillDriving.driving);
        assertEquals(413, stillDriving.selection.stop);
        assertFalse(track.observe(at(18,0,35), "405", "Via Júlia", "Via 2", true));
        assertFalse(track.observe(at(18,0,40), "405", "Girona", "Via 2", true));
        assertFalse(track.observe(at(18,0,40)+10_000, "405", "La Pau", "Via 2", true));
        assertTrue(track.observe(at(18,0,40)+20_000, "405", "La Pau", "Via 2", true));
        CorporateDutyTracker.Snapshot atEndStation = track.at(at(18,0,41));
        assertEquals(CorporateServicePlan.Selection.IDLE, atEndStation.selection.kind);
        assertEquals(413, atEndStation.selection.stop);
        assertEquals(at(18,0,50), atEndStation.selection.takeoverAt);
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
