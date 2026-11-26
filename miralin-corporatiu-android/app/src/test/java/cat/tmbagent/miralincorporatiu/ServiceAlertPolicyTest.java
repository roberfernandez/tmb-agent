package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public final class ServiceAlertPolicyTest {
    private static final String SCHEDULE =
        "1126,1146,EST,413,413,,,;" +
        "1146,1156,TRA,413,417,,,;" +
        "1156,1320,CON,417,413,413,1,2;" +
        "1320,1490,EST,413,413,,,";

    private static long at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0,
            ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
    }

    @Test public void livePickupUsesTheCurrentCorporatePredictionInsteadOfServiceTime() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-25", SCHEDULE);
        CorporateDutyTracker tracker = new CorporateDutyTracker(plan);
        long now = at(25, 19, 20);
        CorporateDutyTracker.Snapshot waiting = tracker.at(now);
        CorporateTrainSelection.Train live = new CorporateTrainSelection.Train(
            "413", "19:28", "05:10", "Poblenou", "Via 1");

        CorporateServicePlan.Notice notice = ServiceAlertPolicy.liveTrainNotice(
            plan, waiting, live, now, at(25, 19, 28), now, 10);
        assertNotNull(notice);
        assertEquals("live-train-2", notice.key);
        assertEquals("19:28", notice.time);
        assertTrue(notice.body.contains("Miralín corporatiu"));

        assertNull(ServiceAlertPolicy.liveTrainNotice(
            plan, waiting, live, now, at(25, 19, 40), now, 10));
    }

    @Test public void trainAlertExpiresAsSoonAsBoardingIsConfirmed() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-25", SCHEDULE);
        CorporateDutyTracker tracker = new CorporateDutyTracker(plan);
        long waitingAt = at(25, 19, 20);
        CorporateDutyTracker.Snapshot waiting = tracker.at(waitingAt);
        assertTrue(ServiceAlertPolicy.isRelevant("live-train-2", waitingAt,
            true, plan, waiting));

        assertTrue(tracker.selectRow(at(25, 19, 24), 2));
        CorporateDutyTracker.Snapshot driving = tracker.at(at(25, 19, 25));
        assertTrue(driving.driving);
        assertFalse(ServiceAlertPolicy.isRelevant("live-train-2", at(25, 19, 25),
            true, plan, driving));
    }

    @Test public void scheduledTransferAlertExpiresAtTheEndOfItsPiece() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-25", SCHEDULE);
        CorporateDutyTracker tracker = new CorporateDutyTracker(plan);
        assertTrue(ServiceAlertPolicy.isRelevant("transfer-1", at(25, 19, 10),
            true, plan, tracker.at(at(25, 19, 10))));
        assertFalse(ServiceAlertPolicy.isRelevant("transfer-1", at(25, 19, 16),
            true, plan, tracker.at(at(25, 19, 16))));
        assertFalse(ServiceAlertPolicy.isRelevant("transfer-1", at(25, 19, 10),
            false, plan, tracker.at(at(25, 19, 10))));
    }

    @Test public void notificationTagCannotLeakIntoANewServicePlan() {
        String tag = ServiceAlertPolicy.tag("oldplan", "live-train-2");
        assertEquals("live-train-2", ServiceAlertPolicy.keyFromTag("oldplan", tag));
        assertNull(ServiceAlertPolicy.keyFromTag("newplan", tag));
    }
}
