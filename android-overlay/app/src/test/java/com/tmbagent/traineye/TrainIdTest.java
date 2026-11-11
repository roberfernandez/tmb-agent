package com.tmbagent.traineye;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class TrainIdTest {
    @Test public void recognizesTheMiralinServiceAliases() {
        assertEquals("406", TrainId.normalize("M06"));
        assertEquals("406", TrainId.normalize("P06"));
        assertEquals("413", TrainId.normalize("M13"));
        assertEquals("413", TrainId.normalize("P13"));
        assertEquals("406", TrainId.normalize("406"));
        assertEquals("406", TrainId.normalize(" m06 "));
        assertEquals("", TrainId.normalize("M00"));
        assertEquals("", TrainId.normalize("P100"));
        assertEquals("", TrainId.normalize("X06"));
        assertEquals("", TrainId.normalize(null));
    }

    @Test public void keepsTheSameMapIdentityWhenThePrefixChanges() throws Exception {
        JSONObject moving = new JSONObject().put("estacion", "Besòs Mar").put("platformTrack", 1);
        JSONObject first = TrainId.positions(new JSONObject().put("M06", moving).put("P13", moving));
        assertSame(moving, first.getJSONObject("406"));
        assertSame(moving, first.getJSONObject("413"));
        JSONObject second = TrainId.positions(new JSONObject().put("P06", moving).put("M13", moving));
        assertSame(moving, second.getJSONObject("406"));
        assertEquals(2, first.length());
        assertEquals(2, second.length());
        assertFalse(second.has("P06"));
    }

    @Test public void followsAnAliasInTheLoadedServiceWithoutChangingTheServiceNumber() {
        ServicePlan plan = new ServicePlan("600,620,EST,413,413,,,;620,690,CON,413,434,M06,2,1");
        assertEquals("406", plan.trainAt(ServicePlanTest.time("2026-11-11T10:25:00"), null));
        assertEquals("406", plan.rows.get(1).train);
        assertTrue(ServicePlan.isTrain("P13"));
    }
}
