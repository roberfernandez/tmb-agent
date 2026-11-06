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
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1389,1409,TRA,423,428,,,;1409,1469,CON,428,413,405,2,RET"), NOW);
        alerts.acceptBoard(board("423", 1, arrival("409", 6), arrival("401", 10), arrival("402", 20), arrival("403", 30), arrival("404", 40)), NOW);
        JSONObject target = board("428", 1, arrival("409", 240), arrival("401", 300), arrival("402", 420), arrival("403", 540), arrival("404", 720));
        target.put("via2", new JSONArray().put(arrival("405", 600)));
        alerts.acceptBoard(target, NOW);
        List<ServiceAlerts.Alert> due = alerts.due(NOW, 5);
        assertEquals(1, due.size());
        assertEquals("Enllaços per prendre el 405", due.get(0).title);
        String body = due.get(0).body;
        assertTrue(body.contains("403 (pas 23:24, arribada 23:33, marge 1 min)"));
        assertTrue(body.contains("402 (pas 23:24, arribada 23:31, marge 3 min)"));
        assertTrue(body.contains("401 (pas 23:24, arribada 23:29, marge 5 min)"));
        assertFalse(body.contains("404 ("));
        assertFalse(body.contains("409 ("));
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
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1389,1409,TRA,417,428,,,;1409,1469,CON,428,434,405,1,1"), NOW);
        alerts.acceptBoard(board("417", 1, arrival("405", 60)), NOW);
        alerts.acceptBoard(board("428", 1, arrival("405", 300)), NOW);
        assertTrue(alerts.advice(alerts.plan.rows.get(1), NOW).contains("mateix 405"));
        assertFalse(alerts.advice(alerts.plan.rows.get(1), NOW).contains("Alternatives:"));
    }

    @Test public void listsTwoTrainsBeforeTheFirstPassesAndThenAlertsAbout406() throws Exception {
        long now = ServicePlanTest.time("2026-11-06T18:56:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1126,1167,TRA,413,428,,,;1167,1245,CON,428,434,402,2,RET"), now);
        assertEquals(java.util.Set.of("413", "428"), alerts.requiredStops(now));
        alerts.acceptBoard(board("413", 1, arrival("405", 150), arrival("406", 330), arrival("407", 420)), now);
        JSONObject joanic = board("428", 1, arrival("405", 1500), arrival("406", 1620), arrival("407", 1860));
        joanic.put("via2", new JSONArray().put(arrival("402", 1860)));
        alerts.acceptBoard(joanic, now);
        assertTrue(alerts.due(now, 2).isEmpty());
        List<ServiceAlerts.Alert> due = alerts.due(now, 5);
        assertEquals(1, due.size());
        ServiceAlerts.Alert list = due.get(0);
        assertEquals("Enllaços per prendre el 402", list.title);
        assertTrue(list.body.contains("La Pau · via 1 → Joanic · presa per via 2"));
        assertTrue(list.body.contains("405 (pas 18:58, arribada 19:21, marge 6 min)"));
        assertTrue(list.body.contains("406 (pas 19:01, arribada 19:23, marge 4 min)"));
        assertFalse(list.body.contains("407 ("));
        assertEquals(now + 150000L, list.expiresAt);
        alerts.markSent(list);
        assertTrue(alerts.due(now + 1000, 5).isEmpty());

        long afterFirst = now + 150000L;
        alerts.acceptBoard(board("413", 1, arrival("406", 180), arrival("407", 270)), afterFirst);
        joanic = board("428", 1, arrival("406", 1470), arrival("407", 1710));
        joanic.put("via2", new JSONArray().put(arrival("402", 1710)));
        alerts.acceptBoard(joanic, afterFirst);
        due = alerts.due(afterFirst, 5);
        assertEquals(1, due.size());
        ServiceAlerts.Alert last = due.get(0);
        assertEquals("Puja al tren 406", last.title);
        assertTrue(last.body.contains("Joanic cap a les 19:23 · marge estimat 4 min per prendre el 402 per via 2"));
        assertEquals(afterFirst + 180000L, last.expiresAt);
        alerts.markSent(last);
        assertTrue(alerts.due(afterFirst + 1000, 5).isEmpty());
    }

    @Test public void ranksNoAlternativesWhenTheHandoverIsAtEitherTerminus() throws Exception {
        for (String terminus : List.of("413", "434")) {
            int transferVia = "413".equals(terminus) ? 2 : 1;
            int takeVia = 3 - transferVia;
            ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(
                "1389,1409,TRA,423," + terminus + ",,,;1409,1469,CON," + terminus + ",423,405," + takeVia + ",RET"), NOW);
            alerts.acceptBoard(board("423", transferVia, arrival("401", 120), arrival("402", 180)), NOW);
            JSONObject destination = board(terminus, transferVia, arrival("401", 300), arrival("402", 420));
            destination.put("via" + takeVia, new JSONArray().put(arrival("405", 600)));
            alerts.acceptBoard(destination, NOW);
            List<ServiceAlerts.Alert> due = alerts.due(NOW, 5);
            assertEquals(1, due.size());
            assertEquals("Servei · presa del 405", due.get(0).title);
            assertFalse(due.get(0).body.contains("Alternatives"));

            alerts.acceptBoard(board("423", transferVia, arrival("405", 120)), NOW);
            assertEquals("Puja al tren 405", alerts.due(NOW, 5).get(0).title);
        }
    }

    @Test public void doesNotRecommendAConnectionWithoutAFreshAssignedArrivalOnTheRightTrack() throws Exception {
        long now = ServicePlanTest.time("2026-11-06T18:58:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1126,1167,TRA,413,428,,,;1167,1245,CON,428,434,402,2,RET"), now);
        alerts.acceptBoard(board("413", 1, arrival("406", 180)), now);
        JSONObject joanic = board("428", 1, arrival("406", 1500), arrival("402", 1740));
        alerts.acceptBoard(joanic, now);
        assertTrue(alerts.due(now, 5).isEmpty());
        joanic.put("via2", new JSONArray().put(arrival("402", 1801)));
        alerts.acceptBoard(joanic, now - 61000);
        assertTrue(alerts.due(now, 5).isEmpty());
    }

    @Test public void requestsTheNextConnectionsBoardsWhileThePreviousHandoverIsRecent() {
        long now = ServicePlanTest.time("2026-11-06T18:52:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1122,1130,CON,416,413,401,2,2;1130,1150,TRA,413,428,,,;1150,1220,CON,428,434,402,2,RET"), now);
        assertEquals(java.util.Set.of("416", "413", "428"), alerts.requiredStops(now));
    }
}
