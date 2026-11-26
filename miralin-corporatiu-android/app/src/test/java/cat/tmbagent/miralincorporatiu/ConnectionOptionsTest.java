package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ConnectionOptionsTest {
    private static CorporateBoardParser.Track track(int via, String... pairs) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2)
            rows.add(Arrays.asList(pairs[i], pairs[i+1], "", ""));
        return new CorporateBoardParser.Track(via, true, Collections.emptyList(), rows);
    }

    @Test public void usesLastThreeCatchableDeparturesInBothDirections() {
        long now = Instant.parse("2026-11-22T20:00:00Z").toEpochMilli();
        for (int via : new int[]{1, 2}) {
            CorporateBoardParser.Track departures = track(via, "408","21:01","409","21:04",
                "M10","21:08","411","21:12","412","21:15","413","21:17");
            departures.rows.get(3).set(2, "05:36");
            CorporateBoardParser.Track arrivals = track(via, "408","21:16","409","21:18",
                "410","21:24","411","21:28","412","21:30");
            CorporateBoardParser.Track opposite = track(3-via, "407","21:30","413","21:25");
            CorporateBoardParser.Board origin = new CorporateBoardParser.Board(
                via == 1 ? departures : opposite, via == 1 ? opposite : departures);
            CorporateBoardParser.Board destination = new CorporateBoardParser.Board(
                via == 1 ? arrivals : opposite, via == 1 ? opposite : arrivals);
            long deadline = now + 30 * 60_000;
            List<ConnectionOptions.Option> options = ConnectionOptions.calculate(origin, destination,
                "407", via, deadline, now);
            assertEquals(3, options.size());
            assertEquals("409", options.get(0).train);
            assertEquals("410", options.get(1).train);
            assertEquals(6 * 60_000, options.get(1).margin);
            assertEquals("411", options.get(2).train);
            assertEquals("05:36", options.get(2).previousTrainGap);
            assertEquals(2 * 60_000, options.get(2).margin);
            assertEquals(now + 12 * 60_000, options.get(2).departure);
            assertTrue(ConnectionOptions.calculate(origin, destination, "407", via, deadline,
                now + 13 * 60_000).isEmpty());
            assertTrue(ConnectionOptions.calculate(origin, destination, "407", via,
                deadline + CorporateProjection.LAP_MS, now).isEmpty());
        }
    }

    @Test public void stationBeforeAnOppositeTrackRelayPollsBothStops() {
        CorporateServicePlan plan = new CorporateServicePlan("2026-11-22",
            "1200,1260,EST,413,413,,,;1260,1290,TRA,413,428,,,;1290,1340,CON,428,413,407,2,RET");
        CorporateDutyTracker duty = new CorporateDutyTracker(plan);
        CorporateDutyTracker.Snapshot snapshot = duty.at(plan.minuteMillis(1250));
        assertEquals(0, snapshot.photoRow);
        assertEquals(CorporateServicePlan.Selection.CONNECTION, snapshot.selection.kind);
        assertTrue(snapshot.selection.watchesTrain());
        assertEquals(413, snapshot.selection.origin);
        assertEquals(428, snapshot.selection.destination);
        assertEquals("407", snapshot.selection.train);
    }
}
