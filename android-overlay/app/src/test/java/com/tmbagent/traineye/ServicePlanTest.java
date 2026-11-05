package com.tmbagent.traineye;

import org.junit.Test;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public class ServicePlanTest {
    static final String SCHEDULE = "1126,1145,EST,413,413,,,;1145,1163,TRA,413,417,,,;1163,1172,CON,417,413,416,2,2;1172,1248,EST,413,413,,,;1248,1264,TRA,413,423,,,;1264,1389,EST,423,423,,,;1389,1409,TRA,423,434,,,;1409,1469,CON,434,413,405,2,RET;1469,1490,EST,413,413,,,";
    static long time(String local) { return ZonedDateTime.parse(local + "+01:00[Europe/Madrid]").toInstant().toEpochMilli(); }
    @Test public void watches405WhileAtJaumeIAndDuringTransfer() {
        ServicePlan plan = new ServicePlan(SCHEDULE);
        assertEquals("405", plan.trainAt(time("2026-11-04T22:23:00"), "403"));
        assertEquals("405", plan.trainAt(time("2026-11-04T23:10:00"), "403"));
        assertEquals("405", plan.trainAt(time("2026-11-04T23:29:00"), "403"));
        assertEquals("416", plan.trainAt(time("2026-11-04T19:23:00"), "403"));
    }
    @Test public void handlesBeforeStartAndAfterMidnightWithoutPhotoDate() {
        ServicePlan plan = new ServicePlan(SCHEDULE);
        assertEquals("416", plan.trainAt(time("2026-11-04T09:00:00"), "403"));
        assertEquals("405", plan.trainAt(time("2026-11-05T00:15:00"), "403"));
        assertEquals(1455, plan.minutesAt(time("2026-11-05T00:15:00")));
        assertEquals(plan.anchorAt(time("2026-11-04T22:00:00")), plan.anchorAt(time("2026-11-05T00:15:00")));
    }
    @Test public void replacingAServiceReplacesItsNextTrain() {
        long now = time("2026-11-04T22:23:00");
        assertEquals("403", new ServicePlan(SCHEDULE.replace(",405,", ",403,")).trainAt(now, null));
        assertEquals("405", new ServicePlan(SCHEDULE).trainAt(now, null));
        assertEquals("423", new ServicePlan(SCHEDULE).sourceFor(new ServicePlan(SCHEDULE).rows.get(7)));
    }
    @Test public void watchesTrain417InTheServicePlan() {
        ServicePlan plan = new ServicePlan(SCHEDULE.replace(",405,", ",417,"));
        assertTrue(ServicePlan.isTrain("417"));
        assertEquals("417", plan.trainAt(time("2026-11-04T22:23:00"), "403"));
        assertEquals("417", plan.trainAt(time("2026-11-05T00:15:00"), "403"));
    }
}
