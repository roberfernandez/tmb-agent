package cat.tmbagent.miralincorporatiu;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Experimental, conservative live activity selection for automatic service following.
 *
 * The planned service never changes. A driving row remains pending until two
 * fresh official corporate observations locate the correct train at its
 * boarding station, or the worker explicitly confirms boarding. An appearance
 * at an earlier lap or an ambiguous/inaccessible board cannot release it.
 */
final class CorporateDutyTracker {
    private static final long EARLY_WINDOW_MS = 20L * 60_000L;
    private static final long FINAL_DELAY_GRACE_MS = 90L * 60_000L;
    private static final long OBSERVATION_TIMEOUT_MS = 45_000L;
    private static final long MIN_OBSERVATION_SPACING_MS = 7_000L;
    private final CorporateServicePlan plan;
    private final List<CorporateServicePlan.Segment> rows;
    private final boolean[] boarded;
    private final String[] evidence;
    private int seenRow = -1;
    private long lastSeenAt = -1;
    private int consecutive;

    static final class Snapshot {
        final int photoRow;
        final int pickupRow;
        final boolean waiting;
        final boolean driving;
        final String train;
        final String from;
        final long plannedStart;
        final String source;
        final CorporateServicePlan.Selection selection;
        Snapshot(int photoRow, int pickupRow, boolean waiting, boolean driving,
                 String train, String from, long plannedStart, String source,
                 CorporateServicePlan.Selection selection) {
            this.photoRow = photoRow;
            this.pickupRow = pickupRow;
            this.waiting = waiting;
            this.driving = driving;
            this.train = train;
            this.from = from;
            this.plannedStart = plannedStart;
            this.source = source;
            this.selection = selection;
        }
    }

    CorporateDutyTracker(CorporateServicePlan plan) {
        this.plan = plan;
        this.rows = plan.viewerSegments();
        this.boarded = new boolean[rows.size()];
        this.evidence = new String[rows.size()];
    }

    /**
     * Prefer the scheduled active driving row, otherwise the next future
     * driving row. Only the last row can continue pending for up to 90 min
     * beyond its scheduled end: a late train must not vanish at midnight.
     */
    Snapshot at(long now) {
        int active = -1, future = -1, last = -1;
        for (int i = 0; i < rows.size(); i++) {
            CorporateServicePlan.Segment r = rows.get(i);
            if (!r.driving()) continue;
            long start = plan.minuteMillis(r.start), end = plan.minuteMillis(r.end);
            if (start <= now && now < end) active = i;
            if (start > now && future == -1) future = i;
            if (start <= now) last = i;
        }
        int focus = active != -1 ? active : future;
        if (focus == -1 && last != -1 && now < plan.minuteMillis(rows.get(last).end) + FINAL_DELAY_GRACE_MS)
            focus = last;
        if (focus == -1) return null;

        CorporateServicePlan.Segment r = rows.get(focus);
        boolean driving = boarded[focus];
        int photoRow;
        if (driving) {
            // Subsequent scheduled station/transfer rows may be highlighted
            // normally, until the next train is due; do not freeze the whole
            // service on a previously completed driving row.
            int scheduled = plan.viewerIndex(now);
            photoRow = scheduled > focus ? scheduled : focus;
        } else {
            int scheduled = plan.viewerIndex(now);
            // Even if the clock moved past the planned pickup, keep highlighting
            // the preceding station row instead of treating driving as started.
            if (scheduled >= focus || scheduled < 0) photoRow = waitingRow(focus);
            else photoRow = scheduled;
        }
        CorporateServicePlan.Selection selected = plan.selectionFor(focus, driving);
        return new Snapshot(photoRow, focus, !driving, driving, r.train, r.from,
            plan.minuteMillis(r.start), driving ? evidence[focus] : "waiting", selected);
    }

    private int waitingRow(int driveIndex) {
        for (int i = driveIndex - 1; i >= 0; i--) {
            CorporateServicePlan.Segment previous = rows.get(i);
            if ("EST".equals(previous.type) && previous.from.equals(rows.get(driveIndex).from)) return i;
            if (previous.driving()) break;
        }
        return Math.max(0, driveIndex - 1);
    }

    /**
     * Called exclusively on a successful, fresh corporate board read while
     * the service still targets the train's boarding station.
     */
    boolean observe(long now, String train, String location, String reportedTrack,
                    boolean unambiguous) {
        Snapshot snapshot = at(now);
        if (snapshot == null || !snapshot.waiting || !unambiguous
            || now < snapshot.plannedStart - EARLY_WINDOW_MS
            || !snapshot.train.equals(train)
            || !sameStation(location, CorporateServicePlan.stationName(snapshot.from))) {
            resetObservations();
            return false;
        }

        CorporateServicePlan.Segment r = rows.get(snapshot.pickupRow);
        // An intermediate-station pickup must match the scheduled platform.
        // Terminal tables can share a platform; only a non-ambiguous reading
        // is required for terminal pickups.
        boolean terminal = "413".equals(r.from) || "434".equals(r.from);
        if (!terminal && !sameTrack(reportedTrack, r.via)) {
            resetObservations();
            return false;
        }

        int index = snapshot.pickupRow;
        if (seenRow == index && lastSeenAt >= 0
            && now - lastSeenAt >= MIN_OBSERVATION_SPACING_MS
            && now - lastSeenAt <= OBSERVATION_TIMEOUT_MS) {
            consecutive++;
        } else {
            consecutive = 1;
        }
        seenRow = index;
        lastSeenAt = now;
        if (consecutive < 2) return false;
        boarded[index] = true;
        evidence[index] = "corporate";
        resetObservations();
        return true;
    }

    boolean confirmBoarded(long now, int row) {
        Snapshot snapshot = at(now);
        if (snapshot == null || snapshot.pickupRow != row
            || now < snapshot.plannedStart - 30L * 60_000L) return false;
        boarded[row] = true;
        evidence[row] = "manual";
        resetObservations();
        return true;
    }

    static boolean sameStation(String reported, String expected) {
        return !normal(reported).isEmpty() && normal(reported).equals(normal(expected));
    }

    private static String normal(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static boolean sameTrack(String reported, String expectedVia) {
        if (reported == null || expectedVia == null) return false;
        String text = reported.toLowerCase(Locale.ROOT).trim();
        return text.equals(expectedVia) || text.matches("via\\s*" + expectedVia);
    }

    private void resetObservations() {
        seenRow = -1;
        consecutive = 0;
        lastSeenAt = -1;
    }
}
