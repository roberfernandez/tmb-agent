package com.tmbagent.traineye;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ManualTrainEtaTest {
    private static final long NOW = 1_794_317_508_000L;

    @Test public void usesTheFirstArrivalOfTheSelectedTrainAcrossBothTracks() throws JSONException {
        ManualTrainEta eta = new ManualTrainEta("423", "410", false);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);
        assertEquals(NOW + 411_000, eta.predictedArrival(boards, NOW, null, 0));

        boards.acceptBoard(board(202, 1100), NOW + 1_000);
        assertEquals(NOW + 203_000, eta.predictedArrival(boards, NOW + 1_000, null, 0));
    }

    @Test public void retainsOnlyTheFirstArrivalWithRecentPositionOnItsApproach() throws JSONException {
        ManualTrainEta eta = new ManualTrainEta("423", "410", false);
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);

        JSONObject via2 = position("Verdaguer", 2);
        assertEquals(NOW + 411_000, eta.predictedArrival(boards, NOW + 60_000, via2, 2_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 61_000, via2, 15_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 61_000, position("Barceloneta", 1), 2_000));
        assertEquals(0, eta.predictedArrival(boards, NOW + 120_000, via2, 2_000));
    }

    @Test public void endsTheCountdownAtTheFirstPassOrWhenTheMapSkipsTheStation() throws JSONException {
        ManualTrainEta eastbound = new ManualTrainEta("423", "410", false);
        assertTrue(eastbound.observe(position("Jaume I", 1)));
        assertFalse(eastbound.isWaiting());
        assertFalse(eastbound.confirm());

        ManualTrainEta westbound = new ManualTrainEta("423", "410", false);
        assertFalse(westbound.observe(position("Passeig de Gràcia", 2)));
        assertTrue(westbound.observe(position("Barceloneta", 2)));
        assertFalse(westbound.isWaiting());

        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(2893, 411), NOW);
        assertEquals(0, westbound.predictedArrival(boards, NOW, null, 0));
        assertFalse(new ManualTrainEta("423", "410", true).isWaiting());
    }

    @Test public void laPauUsesTheInboundForecastWhenThe406IsLeavingOnVia1() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(new JSONObject("{\"stopCode\":\"413\","
            + "\"via1\":[{\"trainCode\":\"M06\",\"secsLeft\":47}],"
            + "\"via2\":[{\"trainCode\":\"P06\",\"secsLeft\":4200}]}"), NOW);
        ManualTrainEta eta = new ManualTrainEta("413", "406", false);
        assertEquals(NOW + 4_200_000L,
            eta.predictedArrival(boards, NOW, position("Besòs Mar", 1), 0));
        assertFalse(eta.observe(position("Besòs Mar", 1)));

        boards.acceptBoard(new JSONObject("{\"stopCode\":\"413\","
            + "\"via1\":[{\"trainCode\":\"P06\",\"secsLeft\":47}],\"via2\":[]}"), NOW);
        assertEquals(0, eta.predictedArrival(boards, NOW, position("Besòs Mar", 1), 0));
    }

    @Test public void theFirstPassAtAnIntermediateStationFollowsTheTrainsDirection() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(board(47, 900), NOW);
        ManualTrainEta eta = new ManualTrainEta("423", "P10", false);
        assertEquals(NOW + 900_000L,
            eta.predictedArrival(boards, NOW, position("Girona", 1), 0));

        boards.acceptBoard(board(900, 47), NOW);
        assertEquals(NOW + 900_000L,
            eta.predictedArrival(boards, NOW, position("Barceloneta", 2), 0));
    }

    @Test public void trinitatUsesTheInboundForecastForTheRenamed413() throws JSONException {
        ServiceAlerts boards = new ServiceAlerts(new ServicePlan(""), NOW);
        boards.acceptBoard(new JSONObject("{\"stopCode\":\"434\","
            + "\"via1\":[{\"trainCode\":\"M13\",\"secsLeft\":600}],"
            + "\"via2\":[{\"trainCode\":\"P13\",\"secsLeft\":30}]}"), NOW);
        ManualTrainEta eta = new ManualTrainEta("434", "413", false);
        assertEquals(NOW + 600_000L,
            eta.predictedArrival(boards, NOW, position("Via Júlia", 2), 0));
    }

    private static JSONObject board(int via1, int via2) throws JSONException {
        return new JSONObject("{\"line\":\"L4\",\"stopCode\":\"423\","
            + "\"via1\":[{\"trainCode\":\"409\",\"secsLeft\":45},{\"trainCode\":\"410\",\"secsLeft\":" + via1 + "}],"
            + "\"via2\":[{\"trainCode\":\"410\",\"secsLeft\":" + via2 + "}]}");
    }

    private static JSONObject position(String station, int track) throws JSONException {
        return new JSONObject().put("estacion", station).put("platformTrack", track);
    }
}
