package cat.tmbagent.miralincorporatiu;

/**
 * Projects a train's official SORTIDA at the selected station by whole L4 laps.
 * The corporate time is live; 77 minutes per remaining lap is a planning estimate,
 * never a measured arrival time. Manual following uses the unmodified official time.
 */
final class CorporateProjection {
    static final long LAP_MS = 77L * 60_000L;
    static final int MAX_LAPS = 2;
    private CorporateProjection() {}

    static final class Result {
        final long timeMs;
        final int laps;
        Result(long timeMs, int laps) {
            this.timeMs = timeMs;
            this.laps = laps;
        }
    }

    /**
     * Pick the next 0/1/2-lap passage closest to the actual upcoming duty time.
     * Ignore already-passed passages. Keeps reacting to delays in official SORTIDA,
     * instead of anchoring future laps to a fixed timetable.
     */
    static Result project(long officialDepartureMs, long nowMs, long takeoverMs) {
        if (officialDepartureMs < 0) return new Result(-1, 0);
        if (takeoverMs <= 0) return new Result(officialDepartureMs, 0);
        long bestTime = -1, bestDistance = Long.MAX_VALUE;
        int bestLaps = 0;
        for (int laps = 0; laps <= MAX_LAPS; laps++) {
            long candidate = officialDepartureMs + laps * LAP_MS;
            if (candidate < nowMs - 60_000L) continue;
            long distance = Math.abs(candidate - takeoverMs);
            // Equal distances favor the earlier passage, not an invented later one.
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTime = candidate;
                bestLaps = laps;
            }
        }
        return new Result(bestTime, bestLaps);
    }
}
