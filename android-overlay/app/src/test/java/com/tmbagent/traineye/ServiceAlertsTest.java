package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ServiceAlertsTest {
    private static final long NOW = ServicePlanTest.time("2026-11-04T23:24:00");
    private static JSONObject arrival(String train, int seconds) throws Exception { return new JSONObject().put("trainCode", train).put("secsLeft", seconds); }
    private static JSONObject board(String stop, int via, JSONObject... entries) throws Exception { return new JSONObject().put("stopCode", stop).put("via" + via, new JSONArray(entries)); }
    private static ServiceAlerts alerts() { return new ServiceAlerts(new ServicePlan(ServicePlanTest.SCHEDULE), NOW); }

    @Test public void warnsAboutOwnTrainPassingJaumeIOnVia1BeforeTrinitatVia2() throws Exception {
        ServiceAlerts alerts = alerts();
        alerts.acceptBoard(board("423", 1, arrival("405", 120)), NOW);
        alerts.acceptBoard(board("434", 2, arrival("405", 1200)), NOW);
        List<ServiceAlerts.Alert> due = alerts.due(NOW, 5);
        assertEquals(1, due.size());
        assertEquals("Puja al tren 405", due.get(0).title);
        assertTrue(due.get(0).body.contains("Jaume I · via 1"));
        assertTrue(due.get(0).body.contains("mateix 405"));
        alerts.markSent(due.get(0));
        assertTrue(alerts.due(NOW + 1000, 5).isEmpty());
    }
    @Test public void ranksThreeOppositeTrackOptionsAgainstLiveAssignedArrival() throws Exception {
        ServiceAlerts alerts = alerts();
        alerts.acceptBoard(board("423", 1, arrival("401", 10), arrival("402", 20), arrival("403", 30), arrival("404", 40)), NOW);
        JSONObject target = board("434", 1, arrival("401", 300), arrival("402", 420), arrival("403", 540), arrival("404", 720));
        target.put("via2", new JSONArray().put(arrival("405", 600)));
        alerts.acceptBoard(target, NOW);
        String advice = alerts.advice(alerts.plan.rows.get(7), NOW);
        assertTrue(advice.contains("403 (23:33, marge 1 min)"));
        assertTrue(advice.contains("402 (23:31, marge 3 min)"));
        assertTrue(advice.contains("401 (23:29, marge 5 min)"));
        assertFalse(advice.contains("404 ("));
    }
    @Test public void staleOrMissingBoardsDoNotInventAlternatives() throws Exception {
        ServiceAlerts alerts = alerts();
        alerts.acceptBoard(board("423", 1, arrival("405", 120)), NOW - 61000);
        alerts.acceptBoard(board("434", 2, arrival("405", 900)), NOW);
        String advice = alerts.advice(alerts.plan.rows.get(7), NOW);
        assertTrue(advice.contains("Sense arribades recents"));
        assertFalse(advice.contains("Darrera opció"));
        assertFalse(alerts.due(NOW, 5).get(0).title.startsWith("Puja"));
    }
    @Test public void transferWarningsRespectMarginAndPersistDeduplication() {
        long now = ServicePlanTest.time("2026-11-04T23:04:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(ServicePlanTest.SCHEDULE), now);
        assertTrue(alerts.due(now, 2).isEmpty());
        List<ServiceAlerts.Alert> due = alerts.due(now, 5);
        assertEquals(1, due.size()); assertTrue(due.get(0).body.contains("Jaume I → Trinitat Nova"));
        alerts.markSent(due.get(0));
        ServiceAlerts restarted = new ServiceAlerts(new ServicePlan(ServicePlanTest.SCHEDULE), now + 1000);
        restarted.sent.addAll(alerts.sent);
        assertTrue(restarted.due(now + 1000, 5).isEmpty());
    }
    @Test public void sameTrackUsesOwnTrainWithoutThreeTrainRanking() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1389,1409,TRA,417,413,,,;1409,1469,CON,413,434,405,2,1"), NOW);
        alerts.acceptBoard(board("417", 2, arrival("405", 60)), NOW);
        alerts.acceptBoard(board("413", 2, arrival("405", 300)), NOW);
        assertTrue(alerts.advice(alerts.plan.rows.get(1), NOW).contains("mateix 405"));
        assertFalse(alerts.advice(alerts.plan.rows.get(1), NOW).contains("Alternatives:"));
    }
}
