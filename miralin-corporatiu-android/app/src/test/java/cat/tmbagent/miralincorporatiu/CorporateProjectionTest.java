package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CorporateProjectionTest {
    private static long minutes(int h, int m) { return (h * 60L + m) * 60_000L; }

    @Test public void choosesOneLapForUpcomingDutyInsteadOfFirstPublishedDeparture() {
        CorporateProjection.Result r = CorporateProjection.project(
            minutes(18, 50), minutes(17, 56), minutes(20, 44));
        assertEquals(minutes(20, 7), r.timeMs);
        assertEquals(1, r.laps);
    }

    @Test public void updatedCorporateTimeShiftsTheProjectionByTheDelay() {
        CorporateProjection.Result r = CorporateProjection.project(
            minutes(18, 57), minutes(17, 56), minutes(20, 44));
        assertEquals(minutes(20, 14), r.timeMs);
        assertEquals(1, r.laps);
    }

    @Test public void choosesTwoLapsWhenTheDutyIsStillFarAway() {
        CorporateProjection.Result r = CorporateProjection.project(
            minutes(18, 0), minutes(17, 30), minutes(20, 40));
        assertEquals(minutes(20, 34), r.timeMs);
        assertEquals(2, r.laps);
    }

    @Test public void ignoresThePublishedDepartureWhenItHasAlreadyPassed() {
        CorporateProjection.Result r = CorporateProjection.project(
            minutes(18, 0), minutes(19, 0), minutes(20, 40));
        assertEquals(minutes(20, 34), r.timeMs);
        assertEquals(2, r.laps);
    }

    @Test public void neverProjectsMissingDataOrManualMode() {
        assertEquals(-1, CorporateProjection.project(-1, minutes(17, 0), minutes(20, 0)).timeMs);
        CorporateProjection.Result r = CorporateProjection.project(
            minutes(18, 50), minutes(17, 56), -1);
        assertEquals(minutes(18, 50), r.timeMs);
        assertEquals(0, r.laps);
    }
}
