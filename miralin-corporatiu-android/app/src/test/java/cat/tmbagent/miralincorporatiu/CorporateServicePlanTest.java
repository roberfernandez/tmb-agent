package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public final class CorporateServicePlanTest {
    private static long at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0,
            ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
    }

    private static final String SCHEDULE =
        "570,600,EST,423,423,,2,;" +
        "600,660,CON,423,434,406,2,1;" +
        "660,670,TRA,434,434,,1,;" +
        "670,690,EST,434,434,,1,;" +
        "690,750,CON,434,413,418,1,RET";

    @Test public void followsNextTrainWhileWaitingAndActiveTrainWhileDriving() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17", SCHEDULE);
        CorporateServicePlan.Selection before = plan.at(at(17, 9, 55));
        assertEquals("406", before.train);
        assertEquals(423, before.stop);
        assertEquals(2, before.via);
        assertFalse(before.driving);

        CorporateServicePlan.Selection during = plan.at(at(17, 10, 30));
        assertEquals("406", during.train);
        assertEquals(434, during.stop);
        assertEquals(0, during.via);
        assertTrue(during.driving);

        CorporateServicePlan.Selection transfer = plan.at(at(17, 11, 2));
        assertEquals("418", transfer.train);
        assertEquals(434, transfer.stop);
        assertEquals(0, transfer.via);
        assertFalse(transfer.driving);

        CorporateServicePlan.Selection second = plan.at(at(17, 11, 45));
        assertEquals("418", second.train);
        assertEquals(413, second.stop);
        assertEquals(0, second.via);
        assertTrue(second.driving);

        assertNull(plan.at(at(17, 12, 31)));
    }

    @Test public void respectsCrossMidnightAndDoesNotKeepYesterdayActive() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17",
            "1400,1490,CON,433,413,407,1,2");
        assertEquals("407", plan.at(at(18, 0, 30)).train);
        assertNull(plan.at(at(18, 1, 00)));
    }

    @Test public void invalidOrUnusableSchedulesAreRejected() {
        try {
            new CorporateServicePlan("2026-11-17", "60,90,EST,413,413,,,");
            fail("Unusable schedules must fail");
        } catch (IllegalArgumentException expected) {}
        try {
            new CorporateServicePlan("today", SCHEDULE);
            fail("Invalid dates must fail");
        } catch (IllegalArgumentException expected) {}
    }

    @Test public void schedulesOnlyTransfersThatDoNotDependOnATrain() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-17", SCHEDULE);
        java.util.List<CorporateServicePlan.Notice> beforeTake = plan.noticesAt(at(17, 9, 55), 5);
        assertTrue(beforeTake.isEmpty());
        java.util.List<CorporateServicePlan.Notice> transfer = plan.noticesAt(at(17, 10, 56), 5);
        assertEquals(1, transfer.size());
        assertEquals("TRASLLAT", transfer.get(0).title);
        assertTrue(transfer.get(0).body.contains("segons servei"));
        assertEquals(0, plan.noticesAt(at(17, 10, 00), 3).stream()
            .filter(n -> n.key.equals("transfer-2")).count());
    }
}
