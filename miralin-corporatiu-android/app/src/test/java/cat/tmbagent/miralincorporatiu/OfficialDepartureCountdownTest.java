package cat.tmbagent.miralincorporatiu;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

public final class OfficialDepartureCountdownTest {
    private static long moment(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0,
            ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
    }

    @Test public void countsToOfficialDepartureAtSecondResolution() {
        long now = moment(2026, 11, 17, 10, 20, 15);
        long target = OfficialDepartureCountdown.departureEpochMs("10:23", now);
        assertEquals("~2'45''", OfficialDepartureCountdown.display(target, now));
        assertEquals("~2'44''", OfficialDepartureCountdown.display(target, now + 1000));
    }

    @Test public void exactServiceMovementCountdownHasNoApproximationMarkerAndAllowsLongLeadTime() {
        long now = moment(2026, 11, 17, 13, 0, 0);
        long target = now + 15L * 60 * 60_000L + 30L * 60_000L;
        assertEquals("15h30", OfficialDepartureCountdown.displayExact(target, now));
        assertEquals("15h29", OfficialDepartureCountdown.displayExact(target, now + 60_000L));
    }

    @Test public void handlesMidnightCrossingCorrectly() {
        long now = moment(2026, 11, 17, 23, 58, 10);
        long target = OfficialDepartureCountdown.departureEpochMs("00:03", now);
        assertEquals("~4'50''", OfficialDepartureCountdown.display(target, now));
    }

    @Test public void doesNotTreatYesterdayAsTomorrow() {
        long now = moment(2026, 11, 17, 0, 1, 0);
        assertEquals(-1, OfficialDepartureCountdown.departureEpochMs("23:59", now));
        assertEquals(-1, OfficialDepartureCountdown.departureEpochMs("not-a-time", now));
    }

    @Test public void cannotInventArrivalTimeFromExpiredDeparture() {
        long now = moment(2026, 11, 17, 10, 22, 0);
        assertEquals(-1, OfficialDepartureCountdown.departureEpochMs("10:20", now));
    }

    @Test public void treatsRecentlyPassedDepartureAsZero() {
        long now = moment(2026, 11, 17, 10, 20, 30);
        long target = OfficialDepartureCountdown.departureEpochMs("10:20", now);
        assertEquals("~0'00''", OfficialDepartureCountdown.display(target, now));
    }
}
