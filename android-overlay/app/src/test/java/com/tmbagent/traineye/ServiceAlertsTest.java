package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ServiceAlertsTest {
    private static final long NOW = ServicePlanTest.time("2026-11-04T23:24:00");
    private static final long MARESME = ServicePlanTest.time("2026-11-06T23:52:00");
    private static final String MARESME_SCHEDULE = "1356,1436,CON,416,416,403,2,2;1436,1445,TRA,416,418,,,;1445,1534,EST,418,418,,,;1534,1605,CON,418,413,403,1,RET";
    private static JSONObject arrival(String train, int seconds) throws Exception { return new JSONObject().put("trainCode", train).put("secsLeft", seconds); }
    private static JSONObject board(String stop, int via, JSONObject... entries) throws Exception { return new JSONObject().put("stopCode", stop).put("via" + via, new JSONArray(entries)); }
    private static JSONObject position(String train, String stop, int via) throws Exception {
        return new JSONObject().put(train, new JSONObject().put("estacion", stop).put("platformTrack", via));
    }
    private static void forecast(ServiceAlerts alerts, long now, String stop, int via, int seconds) throws Exception {
        alerts.acceptMap(position("403", stop, via), now);
        alerts.acceptBoard(board("418", 1, arrival("403", seconds)), now);
    }
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
        assertTrue(alerts.due(NOW, 5).isEmpty());
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
        assertEquals(java.util.Set.of("413", "428"), alerts.requiredStops(now, 5));
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
            assertTrue(alerts.due(NOW, 5).isEmpty());

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
        assertEquals(java.util.Set.of("416", "413", "428"), alerts.requiredStops(now, 5));
    }

    @Test public void comparesPoblenouArrivalWithThe403AfterItsTurnaround() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), MARESME);
        assertEquals(java.util.Set.of("416", "418"), alerts.requiredStops(MARESME, 5));
        JSONObject maresme = board("416", 2, arrival("403", 240));
        maresme.put("via1", new JSONArray().put(arrival("405", 180)).put(arrival("406", 420)).put(arrival("403", 720)));
        alerts.acceptBoard(maresme, MARESME);
        alerts.acceptBoard(board("418", 1, arrival("403", 90), arrival("405", 510), arrival("406", 720), arrival("403", 960)), MARESME);
        assertTrue(alerts.due(MARESME, 3).isEmpty());
        List<ServiceAlerts.Alert> due = alerts.due(MARESME, 5);
        assertEquals(1, due.size());
        ServiceAlerts.Alert alert = due.get(0);
        assertEquals("El 406 passa abans del 403", alert.title);
        assertTrue(alert.body.contains("Maresme | Fòrum · via 1 · pas previst 23:59 · marge estimat de canvi 3 min"));
        assertTrue(alert.body.contains("Poblenou cap a les 00:04 · 4 min abans del 403 (00:08)"));
        assertEquals(MARESME + 240000L, alert.expiresAt);
        alerts.markSent(alert);
        assertTrue(alerts.due(MARESME + 1000, 5).isEmpty());
    }

    @Test public void ignoresAnEarlierTrainThatPassesBeforeTheLiveRelease() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), MARESME);
        JSONObject maresme = board("416", 2, arrival("403", 300));
        maresme.put("via1", new JSONArray().put(arrival("406", 270)).put(arrival("403", 720)));
        alerts.acceptBoard(maresme, MARESME);
        alerts.acceptBoard(board("418", 1, arrival("406", 510), arrival("403", 960)), MARESME);
        List<ServiceAlerts.Alert> due = alerts.due(MARESME, 5);
        assertEquals(1, due.size());
        assertEquals("El 403 és la primera arribada prevista", due.get(0).title);
        assertTrue(due.get(0).body.contains("via 1 · 00:04 i arriba a Poblenou cap a les 00:08"));
    }

    @Test public void keepsTheTransferReminderWhileTheReturnComparisonIsIncomplete() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), MARESME);
        JSONObject maresme = board("416", 2, arrival("403", 240));
        maresme.put("via1", new JSONArray().put(arrival("406", 420)).put(arrival("403", 720)));
        alerts.acceptBoard(maresme, MARESME);
        alerts.acceptBoard(board("418", 1, arrival("403", 960)), MARESME - 61000);
        List<ServiceAlerts.Alert> due = alerts.due(MARESME, 5);
        assertEquals(1, due.size());
        assertEquals("Servei · trasllat", due.get(0).title);
        alerts.markSent(due.get(0));

        alerts.acceptBoard(board("418", 1, arrival("403", 960)), MARESME);
        assertTrue(alerts.due(MARESME + 1000, 5).isEmpty());
        alerts.acceptBoard(board("418", 1, arrival("406", 720), arrival("403", 960)), MARESME);
        assertEquals("El 406 passa abans del 403", alerts.due(MARESME + 1000, 5).get(0).title);
    }

    @Test public void checksTheActualReleaseTrackAndSupportsTheReverseDirection() throws Exception {
        for (String exitVia : List.of("1", "2", "RET")) {
            ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(
                "1356,1436,CON,416,428,411,2," + exitVia + ";1436,1445,TRA,428,417,,,"), MARESME);
            JSONObject joanic = board("428", 1, arrival("411", 240));
            joanic.put("via2", new JSONArray().put(arrival("414", 420)).put(arrival("411", 720)));
            alerts.acceptBoard(joanic, MARESME);
            alerts.acceptBoard(board("417", 2, arrival("414", 960), arrival("411", 1200)), MARESME);
            List<ServiceAlerts.Alert> due = alerts.due(MARESME, 5);
            assertEquals(1, due.size());
            if ("1".equals(exitVia)) {
                assertEquals(java.util.Set.of("428", "417"), alerts.requiredStops(MARESME, 5));
                assertEquals("El 414 passa abans del 411", due.get(0).title);
                assertTrue(due.get(0).body.contains("Joanic · via 2"));
            } else {
                assertTrue(alerts.requiredStops(MARESME, 5).isEmpty());
                assertEquals("Servei · trasllat", due.get(0).title);
            }
        }
    }

    @Test public void canStartFollowingAfterMidnightBeforeADelayedRelease() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T00:00:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(
            "1356,1436,CON,416,416,403,2,2;1436,1445,TRA,416,418,,,;1445,1534,EST,418,418,,,"), now);
        assertTrue(alerts.hasPending(now));
        assertEquals(java.util.Set.of("416", "418"), alerts.requiredStops(now, 5));
        JSONObject maresme = board("416", 2, arrival("403", 120));
        maresme.put("via1", new JSONArray().put(arrival("403", 480)));
        alerts.acceptBoard(maresme, now);
        alerts.acceptBoard(board("418", 1, arrival("403", 720)), now);
        List<ServiceAlerts.Alert> due = alerts.due(now, 5);
        assertEquals(1, due.size());
        assertEquals("El 403 és la primera arribada prevista", due.get(0).title);
        assertTrue(due.get(0).body.contains("Poblenou cap a les 00:12"));
        alerts.markSent(due.get(0));
        assertFalse(alerts.hasPending(now + 1000));
    }

    @Test public void alertsTenMinutesBeforeTheMatchedPassEvenFiveStationsAway() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:14:59");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), now);
        assertEquals(java.util.Set.of("418"), alerts.requiredStops(now, 10));
        alerts.acceptMap(position("403", "La Pau", 1), now);
        alerts.acceptBoard(board("418", 1, arrival("403", 181), arrival("403", 601)), now);
        assertTrue(alerts.due(now, 10).isEmpty());

        List<ServiceAlerts.Alert> due = alerts.due(now + 1000, 10);
        assertEquals(1, due.size());
        ServiceAlerts.Alert alert = due.get(0);
        assertEquals("Servei · presa del 403", alert.title);
        assertTrue(alert.body.contains("Poblenou · via 1 · arribada estimada 01:25"));
        assertTrue(alert.body.contains("Presa prevista al servei a les 01:34"));
        assertEquals(now + 601000L, alert.expiresAt);
        alerts.markSent(alert);
        assertTrue(alerts.due(now + 2000L, 10).isEmpty());
    }

    @Test public void warnsImmediatelyWhenThePredictionJumpsInsideTheMargin() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:13:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), now);
        forecast(alerts, now, "La Pau", 1, 720);
        assertTrue(alerts.due(now, 10).isEmpty());
        forecast(alerts, now + 60000, "Besos", 1, 420);
        assertTrue(alerts.due(now + 60000, 10).get(0).body.contains("arribada estimada 01:21"));
    }

    @Test public void postponesTheFirstNoticeAndKeepsWatchingPastTheScheduledTime() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:24:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), now);
        forecast(alerts, now, "Besòs Mar", 1, 900);
        assertTrue(alerts.due(now, 10).isEmpty());
        long delayed = now + 8 * 60000;
        forecast(alerts, delayed, "Maresme", 1, 28 * 60);
        assertTrue(alerts.due(delayed, 10).isEmpty());
        long inside = now + 26 * 60000;
        assertTrue(alerts.hasPending(inside));
        assertEquals(java.util.Set.of("418"), alerts.requiredStops(inside, 10));
        forecast(alerts, inside, "Maresme | Fòrum", 1, 600);
        assertTrue(alerts.due(inside, 10).get(0).body.contains("arribada estimada 02:00"));
    }

    @Test public void correctsAMeaningfulAdvanceAndRearmsAfterALongerDelay() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:24:00");
        ServicePlan plan = new ServicePlan(MARESME_SCHEDULE);
        ServiceAlerts alerts = new ServiceAlerts(plan, now);
        forecast(alerts, now, "Besòs", 1, 600);
        alerts.markSent(alerts.due(now, 10).get(0));

        long after = now + 60000;
        forecast(alerts, after, "Besòs Mar", 1, 480);
        assertTrue(alerts.due(after, 10).isEmpty());
        forecast(alerts, after, "Besòs Mar", 1, 420);
        ServiceAlerts.Alert advance = alerts.due(after, 10).get(0);
        assertEquals("Nova previsió · presa del 403", advance.title);
        assertTrue(advance.body.contains("arribada estimada 01:32"));
        alerts.markSent(advance);

        long delayed = now + 3 * 60000;
        forecast(alerts, delayed, "El Maresme - Fòrum", 1, 900);
        assertTrue(alerts.due(delayed, 10).isEmpty());

        ServiceAlerts restarted = new ServiceAlerts(plan, delayed);
        restarted.restoreTakeState(alerts.changedTakeState());
        long inside = now + 8 * 60000;
        forecast(restarted, inside, "Selva de Mar", 1, 600);
        ServiceAlerts.Alert reentry = restarted.due(inside, 10).get(0);
        assertTrue(reentry.body.contains("arribada estimada 01:42"));
        restarted.markSent(reentry);
        assertTrue(restarted.due(inside + 1000, 10).isEmpty());
    }

    @Test public void doesNotUseMissingStaleOrWrongTrackForecasts() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:24:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), now);
        assertTrue(alerts.due(now, 10).isEmpty());
        alerts.acceptMap(position("403", "Selva de Mar", 1), now);
        alerts.acceptBoard(board("418", 2, arrival("403", 120)), now);
        assertTrue(alerts.due(now, 10).isEmpty());
        forecast(alerts, now, "Selva de Mar", 2, 120);
        assertTrue(alerts.due(now, 10).isEmpty());
        alerts.acceptMap(position("403", "Selva de Mar", 1), now - 45000);
        assertTrue(alerts.due(now, 10).isEmpty());
        forecast(alerts, now, "Selva de Mar", 1, 120);
        alerts.acceptMap(position("403", "Selva de Mar", 1), now + 60000);
        assertTrue(alerts.due(now + 60000, 10).isEmpty());
        alerts.clearLiveData();
        assertTrue(alerts.due(now, 10).isEmpty());
    }

    @Test public void finishesTheTakeAfterTheTrainPassesEvenWhenNoNoticeWasSent() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:24:00");
        ServicePlan plan = new ServicePlan(MARESME_SCHEDULE);
        ServiceAlerts alerts = new ServiceAlerts(plan, now);
        forecast(alerts, now, "Selva de Mar", 1, 120);
        assertEquals(1, alerts.due(now, 10).size());

        long passed = now + 3 * 60000;
        forecast(alerts, passed, "Llacuna", 1, 600);
        assertTrue(alerts.due(passed, 10).isEmpty());
        assertFalse(alerts.hasPending(passed));
        assertTrue(alerts.requiredStops(passed, 10).isEmpty());

        ServiceAlerts restarted = new ServiceAlerts(plan, passed);
        restarted.restoreTakeState(alerts.changedTakeState());
        forecast(restarted, passed, "Selva de Mar", 1, 300);
        assertTrue(restarted.due(passed, 10).isEmpty());
    }

    @Test public void canStartTrackingADelayedDirectTakeAfterItsScheduledStart() throws Exception {
        long now = ServicePlanTest.time("2026-11-07T01:40:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(MARESME_SCHEDULE), now);
        forecast(alerts, now, "Selva de Mar", 1, 180);
        assertTrue(alerts.due(now, 10).get(0).body.contains("arribada estimada 01:43"));
    }

    @Test public void matchesABoardingTrackAtATerminusAndFinishesAfterDeparture() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("1409,1469,CON,434,423,405,2,1"), NOW);
        alerts.acceptMap(position("405", "Via Júlia", 1), NOW);
        alerts.acceptBoard(board("434", 2, arrival("405", 300)), NOW);
        assertEquals(1, alerts.due(NOW, 5).size());

        long after = NOW + 8 * 60000;
        alerts.acceptMap(position("405", "Via Júlia", 2), after);
        alerts.acceptBoard(board("434", 2, arrival("405", 300)), after);
        assertTrue(alerts.due(after, 5).isEmpty());
        assertFalse(alerts.hasPending(after));
    }
    @Test public void waitingInLaPauUsesTheConfiguredMarginForTheAssignedLapOnly() throws Exception {
        ServicePlan plan = new ServicePlan("1126,1317,EST,413,413,,,;1317,1485,CON,413,413,407,2,RET");
        long first = ServicePlanTest.time("2026-11-10T20:35:00");
        ServiceAlerts alerts = new ServiceAlerts(plan, first);
        alerts.acceptMap(position("407", "Besòs", 2), first);
        alerts.acceptBoard(board("413", 2, arrival("407", 300)), first);
        assertTrue(alerts.due(first, 10).isEmpty());

        long passed = first + 5 * 60_000L;
        alerts.acceptMap(position("407", "La Pau", 2), passed);
        assertTrue(alerts.due(passed, 10).isEmpty());

        long before = ServicePlanTest.time("2026-11-10T21:46:59");
        alerts.acceptMap(position("407", "Bogatell", 2), before);
        alerts.acceptBoard(board("413", 2, arrival("407", 601)), before);
        assertTrue(alerts.due(before, 10).isEmpty());
        List<ServiceAlerts.Alert> due = alerts.due(before + 1000, 10);
        assertEquals(1, due.size());
        assertEquals("Servei · presa del 407", due.get(0).title);
        assertTrue(due.get(0).body.contains("La Pau · via 2 · arribada estimada 21:57"));
    }

    @Test public void sameTrackNotificationIsTimedToTheAssignedTrainAtTheOrigin() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(
            "1389,1409,TRA,417,428,,,;1409,1469,CON,428,434,405,1,1"), NOW);
        alerts.acceptBoard(board("417", 1, arrival("401", 30), arrival("405", 301)), NOW);
        alerts.acceptBoard(board("428", 1, arrival("401", 420), arrival("405", 900)), NOW);
        assertTrue(alerts.due(NOW, 5).isEmpty());
        List<ServiceAlerts.Alert> due = alerts.due(NOW + 1000, 5);
        assertEquals(1, due.size());
        assertEquals("Puja al tren 405", due.get(0).title);
        assertTrue(due.get(0).body.contains("Selva de Mar · via 1 a les 23:29"));
        assertFalse(due.get(0).body.contains("401"));
    }

    @Test public void oppositeTrackConnectionsWaitUntilTheAssignedLapIsInTheStationBoard() throws Exception {
        long now = ServicePlanTest.time("2026-11-10T20:05:00");
        ServicePlan plan = new ServicePlan("1204,1290,TRA,426,428,,,;1290,1380,CON,428,413,407,2,RET");
        ServiceAlerts alerts = new ServiceAlerts(plan, now);
        alerts.acceptMap(position("407", "Alfons X", 2), now);
        alerts.acceptBoard(board("426", 1, arrival("401", 120), arrival("402", 180), arrival("403", 240)), now);
        JSONObject joanic = board("428", 1, arrival("401", 300), arrival("402", 360), arrival("403", 420));
        joanic.put("via2", new JSONArray().put(arrival("407", 480)));
        alerts.acceptBoard(joanic, now);
        assertTrue(alerts.due(now, 5).isEmpty());

        long passed = now + 480000L;
        alerts.acceptMap(position("407", "Joanic", 2), passed);
        assertTrue(alerts.due(passed, 5).isEmpty());

        long next = ServicePlanTest.time("2026-11-10T21:20:00");
        alerts.acceptMap(position("407", "Alfons X", 2), next);
        alerts.acceptBoard(board("426", 1, arrival("401", 120), arrival("402", 180), arrival("403", 240)), next);
        joanic.put("via2", new JSONArray().put(arrival("407", 600)));
        alerts.acceptBoard(joanic, next);
        List<ServiceAlerts.Alert> due = alerts.due(next, 5);
        assertEquals(1, due.size());
        assertEquals("Enllaços per prendre el 407", due.get(0).title);
        assertTrue(due.get(0).body.contains("403 (pas 21:24, arribada 21:27, marge 3 min)"));
        assertTrue(due.get(0).body.contains("402 (pas 21:23, arribada 21:26, marge 4 min)"));
        assertTrue(due.get(0).body.contains("401 (pas 21:22, arribada 21:25, marge 5 min)"));
    }

    @Test public void theLoaded406ServiceMatchesM06AndP06InBoardsAndMaps() throws Exception {
        long now = ServicePlanTest.time("2026-11-11T10:25:00");
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(
            "600,630,EST,413,413,,,;630,700,CON,413,434,406,2,1"), now);
        alerts.acceptMap(position("M06", "Besòs", 2), now);
        alerts.acceptBoard(board("413", 2, arrival("P06", 300)), now);
        List<ServiceAlerts.Alert> due = alerts.due(now, 5);
        assertEquals(1, due.size());
        assertEquals("Servei · presa del 406", due.get(0).title);
        assertTrue(alerts.boardStatus("413", "406", now).contains("MATCH train=406"));

        ServiceAlerts lineBoard = new ServiceAlerts(new ServicePlan(""), now);
        JSONObject fallback = new JSONObject().put("stopCode", "413").put("via2", new JSONArray())
            .put("lineTrains", new JSONArray().put(
                new JSONObject().put("tren", "M13").put("stopCode", "413").put("platformTrack", 2).put("secsLeft", 120)));
        lineBoard.acceptBoard(fallback, now);
        assertEquals(now + 120_000L, lineBoard.predictedArrival("413", "2", "P13", now));
    }

    @Test public void acceptsNumericStringArrivalTimeLikeBackend() throws Exception {
        long now = 1800000000000L;
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST,413,413,,,;630,690,CON,413,434,407,1,1"), now);
        JSONObject board = new JSONObject();
        board.put("stopCode", "413");
        board.put("via1", new org.json.JSONArray().put(
            new JSONObject().put("trainCode", "407").put("arrivalTime", String.valueOf(now + 300000L))));
        alerts.acceptBoard(board, now);
        assertEquals(now + 300000L, alerts.predictedArrival("413", "1", "407", now));
    }

    @Test public void findsTrain407OnVia2EvenWhenServiceExpectsVia1() throws Exception {
        long now = 1800000000000L;
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST,413,413,,,;630,690,CON,413,434,407,1,1"), now);
        JSONObject board = new JSONObject().put("stopCode", "413")
            .put("via1", new org.json.JSONArray())
            .put("via2", new org.json.JSONArray().put(
                new JSONObject().put("trainCode", "407").put("secsLeft", 300)));
        alerts.acceptBoard(board, now);
        assertEquals(0L, alerts.predictedArrival("413", "1", "407", now));
        assertEquals(now + 300000L, alerts.predictedArrivalEitherTrack("413", "1", "407", now));
        assertEquals(now + 300000L, alerts.predictedArrivalEitherTrack("413", "", "407", now));
    }

    @Test public void terminalStationsIgnoreViaAndUseEarliestMatchingTrain() throws Exception {
        long now = 1800000000000L;
        for (String stop : new String[]{"413", "434"}) {
            ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST," + stop + "," + stop + ",,,"), now);
            JSONObject board = new JSONObject().put("stopCode", stop)
                .put("via1", new org.json.JSONArray().put(
                    new JSONObject().put("trainCode", "407").put("secsLeft", 480)))
                .put("via2", new org.json.JSONArray().put(
                    new JSONObject().put("trainCode", "407").put("secsLeft", 300)));
            alerts.acceptBoard(board, now);
            assertEquals(now + 300000L, alerts.predictedArrivalEitherTrack(stop, "1", "407", now));
            assertEquals(now + 300000L, alerts.predictedArrivalEitherTrack(stop, "2", "407", now));
        }
    }

    @Test public void usesExplicitlyScopedLineTrainsWhenPlatformArrayIsEmpty() throws Exception {
        long now = 1800000000000L;
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan("600,630,EST,413,413,,,"), now);
        JSONObject board = new JSONObject().put("stopCode", "413")
            .put("via2", new org.json.JSONArray())
            .put("lineTrains", new org.json.JSONArray().put(
                new JSONObject().put("trainCode", "407").put("stopCode", "413").put("platformTrack", 2).put("secsLeft", 180)));
        alerts.acceptBoard(board, now);
        assertEquals(now + 180000L, alerts.predictedArrival("413", "2", "407", now));
        assertEquals(now + 180000L, alerts.predictedArrivalEitherTrack("413", "1", "407", now));
    }

    @Test public void doesNotUseAnUnscopedLineTrainTimerForLaPau() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = board("413", 2).put("lineTrains", new JSONArray().put(
            arrival("M06", 182).put("platformTrack", 2)));
        alerts.acceptBoard(raw, NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "406", NOW));
        assertEquals(0, alerts.predictedArrivalEitherTrack("413", "", "P06", NOW));
        assertTrue(alerts.boardStatus("413", "406", NOW).contains("rejected: lineTrains has no destination stop"));
    }

    @Test public void doesNotUseATrinitatForecastAsALaPauForecast() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = board("413", 2).put("lineTrains", new JSONArray().put(
            arrival("M06", 182).put("platformTrack", 2).put("stopCode", "434")));
        alerts.acceptBoard(raw, NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "406", NOW));
        alerts.acceptBoard(board("413", 2, arrival("P06", 182).put("stationId", "434")), NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "406", NOW));
    }

    @Test public void doesNotTreatATrainIdAsAStationLabel() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = board("413", 2).put("lineTrains", new JSONArray().put(
            new JSONObject().put("id", "413").put("secsLeft", 182).put("platformTrack", 2)));
        alerts.acceptBoard(raw, NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "413", NOW));
    }

    @Test public void guessedUnlabelledBoardsNeedAnExplicitEntryDestination() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = new JSONObject().put("via2", new JSONArray().put(arrival("406", 182)));
        alerts.acceptBoard("413", raw, NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "406", NOW));
        JSONObject diagnostic = alerts.diagnosticBoard("413");
        assertEquals("413", diagnostic.getString("requestedStop"));
        assertEquals("", diagnostic.getString("declaredStop"));
        assertFalse(diagnostic.getJSONObject("raw").has("stopCode"));
        assertEquals(182, diagnostic.getJSONObject("raw").getJSONArray("via2").getJSONObject(0).getInt("secsLeft"));

        raw.put("via2", new JSONArray().put(arrival("P06", 1800)
            .put("stationCode", new JSONObject().put("code", "413"))));
        alerts.acceptBoard("413", raw, NOW);
        assertEquals(NOW + 1_800_000L, alerts.predictedArrival("413", "2", "406", NOW));
    }

    @Test public void aMismatchedBoardCannotBeRelabelledAsLaPau() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        alerts.acceptBoard("413", board("434", 2, arrival("406", 182).put("stopCode", "413")), NOW);
        assertEquals(0, alerts.predictedArrival("413", "2", "406", NOW));
        assertEquals("434", alerts.diagnosticBoard("413").getString("declaredStop"));
    }

    @Test public void labelledNestedStationBoardsKeepValidForecasts() throws Exception {
        ServiceAlerts alerts = new ServiceAlerts(new ServicePlan(""), NOW);
        JSONObject raw = new JSONObject().put("stopCode", "413")
            .put("data", new JSONObject().put("via2", new JSONArray().put(arrival("M06", 1800))));
        alerts.acceptBoard("413", raw, NOW);
        assertEquals(NOW + 1_800_000L, alerts.predictedArrival("413", "2", "406", NOW));
        assertTrue(alerts.diagnosticBoard("413").getJSONObject("raw").has("data"));
    }

    @Test public void liveLaPauBoardMatchesAssignedTrainAndDoesNotConfuseOtherTrains() throws Exception {
        long now = ServicePlanTest.time("2026-11-09T20:28:58");
        ServiceAlerts eta = new ServiceAlerts(new ServicePlan("1126,1317,EST,413,413,,,;1317,1485,CON,413,413,407,2,RET"), now);
        JSONObject board = new JSONObject().put("line", "L4").put("stopCode", "413")
            .put("via1", new JSONArray())
            .put("via2", new JSONArray()
                .put(new JSONObject().put("tren", "411").put("trainCode", "411")
                    .put("secsLeft", 181).put("arrivalTime", now + 181000L).put("track", 2))
                .put(new JSONObject().put("tren", "407").put("trainCode", "407")
                    .put("secsLeft", 425).put("arrivalTime", now + 425000L).put("track", 2)));
        eta.acceptBoard(board, now);
        assertEquals(now + 425000L, eta.predictedArrivalEitherTrack("413", "2", "407", now));
        assertEquals(now + 181000L, eta.predictedArrivalEitherTrack("413", "2", "411", now));
        assertEquals(0, eta.predictedArrivalEitherTrack("413", "2", "409", now));
        assertTrue(eta.boardStatus("413", "407", now).contains("MATCH train=407"));
    }
}
