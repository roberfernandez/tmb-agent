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
    private boolean previewOnly;

    boolean isPreviewOnly() { return previewOnly; }
    private final List<CorporateServicePlan.Segment> rows;
    private final boolean[] boarded;
    private final boolean[] skipped;
    private final String[] evidence;
    private int linkedTransfer = -1;
    private boolean riding;
    private long approachSeenAt = -1;
    private int pendingDriving = -1;
    private final boolean[] drivingArrived;
    private int seenRow = -1;
    private long lastSeenAt = -1;
    private int consecutive;
    private int pendingRow = -1;
    private int selectedRow = -1;
    private long selectedAt = -1;

    /**
     * A manual checkpoint is never a Stop command. Automatic TMB observations
     * continue and take over at the next verified transition.
     */
    boolean selectRow(long now, int index) {
        if (index < 0 || index >= rows.size()) return false;
        CorporateServicePlan.Segment row = rows.get(index);
        long start = plan.minuteMillis(row.start);
        long first = plan.minuteMillis(rows.get(0).start);
        long last = plan.minuteMillis(rows.get(rows.size() - 1).end);
        // A late driving checkpoint is authoritative during the real service.
        // Keep preview behavior for tests before the shift or far past its end.
        if (now < first - FINAL_DELAY_GRACE_MS
            || now > last + FINAL_DELAY_GRACE_MS
            || (row.driving() && now < start - 30L * 60_000L))
            previewOnly = true;
        // If the worker explicitly selects a later stage, earlier missed
        // pickups must not hold the watcher on the wrong train.
        for (int i = 0; i < index; i++)
            if (rows.get(i).driving() && !boarded[i]) skipped[i] = true;
        pendingRow = -1;
        pendingDriving = -1;
        if (row.driving()) {
            boarded[index] = true;
            drivingArrived[index] = false;
            evidence[index] = "manual";
        }
        linkedTransfer = -1;
        approachSeenAt = -1;
        riding = false;
        if (plan.sameTrainTransfer(index)) {
            linkedTransfer = index;
            riding = true;
        }
        selectedRow = index;
        selectedAt = now;
        resetObservations();
        return true;
    }

    private boolean manualCheckpointActive(long now) {
        if (selectedRow < 0) return false;
        if (rows.get(selectedRow).driving()) return !drivingArrived[selectedRow];
        CorporateServicePlan.Segment chosen = rows.get(selectedRow);
        if ("TRA".equals(chosen.type)) {
            long end = plan.minuteMillis(chosen.end);
            if (selectedAt >= end || now < end) return true;
            selectedRow = -1;
            return false;
        }
        // Later verified boarding supersedes a manually marked station even if
        // that boarding was delayed beyond its scheduled start.
        for (int i = selectedRow + 1; i < rows.size(); i++) {
            if (rows.get(i).driving() && boarded[i]) {
                selectedRow = -1;
                return false;
            }
        }
        // Clock-only changes remain fine for transfers/stations; never release
        // a waiting station into an unconfirmed driving row on the clock alone.
        for (int i = selectedRow + 1; i < rows.size(); i++) {
            CorporateServicePlan.Segment later = rows.get(i);
            if (plan.minuteMillis(later.start) > selectedAt
                && now >= plan.minuteMillis(later.start)
                && (!later.driving() || boarded[i])) {
                selectedRow = -1;
                return false;
            }
        }
        return true;
    }

    static final class Snapshot {
        final int photoRow;
        final int pickupRow;
        final boolean waiting;
        final boolean driving;
        final String train;
        final String from;
        final long plannedStart;
        final String source;
        final boolean preview;
        final CorporateServicePlan.Selection selection;
        Snapshot(int photoRow, int pickupRow, boolean waiting, boolean driving,
                 String train, String from, long plannedStart, String source,
                 boolean preview, CorporateServicePlan.Selection selection) {
            this.photoRow = photoRow;
            this.pickupRow = pickupRow;
            this.waiting = waiting;
            this.driving = driving;
            this.train = train;
            this.from = from;
            this.plannedStart = plannedStart;
            this.source = source;
            this.preview = preview;
            this.selection = selection;
        }
    }

    CorporateDutyTracker(CorporateServicePlan plan) { this(plan, false); }

    CorporateDutyTracker(CorporateServicePlan plan, boolean previewOnly) {
        this.plan = plan;
        this.previewOnly = previewOnly;
        this.rows = plan.viewerSegments();
        this.boarded = new boolean[rows.size()];
        this.skipped = new boolean[rows.size()];
        this.evidence = new String[rows.size()];
        this.drivingArrived = new boolean[rows.size()];
    }

    /** Seed a newly started service from its current time slot, once. */
    void resumeAt(long now) {
        if (previewOnly) return;
        for (int i = 0; i < rows.size(); i++) {
            CorporateServicePlan.Segment row = rows.get(i);
            if (now < plan.minuteMillis(row.start)) return;
            if (now >= plan.minuteMillis(row.end)) continue;
            if (row.driving()) {
                boarded[i] = true;
                evidence[i] = "schedule-resume";
                pendingDriving = i;
            } else if (plan.sameTrainTransfer(i)) {
                linkedTransfer = i;
                riding = true;
            }
            return;
        }
    }

    /**
     * Prefer the scheduled active driving row, otherwise the next future
     * driving row. Only the last row can continue pending for up to 90 min
     * beyond its scheduled end: a late train must not vanish at midnight.
     */
    Snapshot at(long now) {
        int activeCon = -1, activeTransfer = -1, activeStation = -1;
        int next = -1, lastDriving = -1;
        long nextStart = Long.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            CorporateServicePlan.Segment r = rows.get(i);
            long start = plan.minuteMillis(r.start), end = plan.minuteMillis(r.end);
            if (start <= now && now < end) {
                if (r.driving()) activeCon = i;
                else if ("TRA".equals(r.type)) activeTransfer = i;
                else if ("EST".equals(r.type)) activeStation = i;
            }
            if (start > now && start < nextStart) { next = i; nextStart = start; }
            if (r.driving() && start <= now) lastDriving = i;
        }
        long serviceEnd = plan.minuteMillis(rows.get(rows.size() - 1).end);
        if (now >= serviceEnd + FINAL_DELAY_GRACE_MS && !previewOnly
            && !(selectedRow >= 0 && rows.get(selectedRow).driving()
                && !drivingArrived[selectedRow])) return null;

        if (!previewOnly && (pendingDriving < 0 || drivingArrived[pendingDriving])
            && (selectedRow < 0 || !rows.get(selectedRow).driving())) {
            int candidate = activeTransfer;
            if (activeStation >= 0 && candidate < 0) candidate = activeStation + 1;
            if (candidate < 0 && activeCon >= 0) candidate = activeCon - 1;
            if (linkedTransfer < 0 && plan.sameTrainTransfer(candidate)
                && !boarded[candidate + 1] && !skipped[candidate + 1]
                && (selectedRow < 0 || selectedRow == candidate || selectedRow == candidate - 1))
                linkedTransfer = candidate;
            // An explicit row selection has priority over the clock-based
            // transfer resolver. This is essential when the pickup is delayed:
            // the user may select the preceding EST/TRA row again even though
            // the printed CON start time has already passed.
            if (linkedTransfer >= 0 && !boarded[linkedTransfer + 1]
                && !manualCheckpointActive(now)) {
                int i = linkedTransfer;
                CorporateServicePlan.Segment leg = rows.get(i), drive = rows.get(i + 1);
                int origin = Integer.parseInt(leg.from), dest = Integer.parseInt(leg.to);
                int photo = !riding && i > 0 && "EST".equals(rows.get(i-1).type) ? i-1 : i;
                CorporateServicePlan.Selection target = riding ? plan.selectionFor(i+1, false)
                    : new CorporateServicePlan.Selection(drive.train, origin, Integer.parseInt(drive.via),
                        false, plan.minuteMillis(leg.start), CorporateServicePlan.Selection.TRANSFER_PICKUP,
                        i, origin, dest);
                return new Snapshot(photo, i+1, true, false, drive.train, drive.from,
                    target.takeoverAt, riding ? "transfer-aboard" : "transfer-wait", false, target);
            }
        }

        // A tapped row is an explicit position checkpoint. It does not stop
        // polling: it only tells the resolver which service segment to follow.
        if (manualCheckpointActive(now)) {
            CorporateServicePlan.Segment chosen = rows.get(selectedRow);
            if (chosen.driving()) return drivingSnapshot(now, selectedRow);
            if ("TRA".equals(chosen.type)) return transferSnapshot(now, selectedRow);
            int following = nextOperationalRow(selectedRow);
            if (following >= 0) {
                CorporateServicePlan.Segment nextRow = rows.get(following);
                if (nextRow.driving()) return drivingSnapshot(now, following);
                return transferSnapshot(now, following, selectedRow);
            }
            return idleSnapshot(now, selectedRow, Integer.parseInt(chosen.from),
                plan.minuteMillis(chosen.end), "manual-station");
        }

        // While a transfer is active, only confirmed driving takes precedence.
        int focus = activeTransfer < 0 || (activeCon >= 0 && boarded[activeCon])
            ? activeCon : -1;
        if (activeTransfer < 0 && pendingRow >= 0 && pendingRow < rows.size()
            && !boarded[pendingRow] && !skipped[pendingRow]) focus = pendingRow;
        // Once aboard, do not abandon this train at the scheduled end or when
        // a later piece overlaps: keep the driving activity until arrival.
        if (pendingDriving >= 0 && !drivingArrived[pendingDriving]) focus = pendingDriving;
        if (focus >= 0) {
            if (drivingArrived[focus]) {
                CorporateServicePlan.Segment row = rows.get(focus);
                return idleSnapshot(now, focus, Integer.parseInt(row.to),
                    plan.minuteMillis(row.end), "driving-arrived");
            }
            return drivingSnapshot(now, focus);
        }
        if (pendingDriving >= 0 && !drivingArrived[pendingDriving])
            return drivingSnapshot(now, pendingDriving);
        if (pendingDriving >= 0 && drivingArrived[pendingDriving]
            && now < plan.minuteMillis(rows.get(pendingDriving).end)) {
            CorporateServicePlan.Segment row = rows.get(pendingDriving);
            return idleSnapshot(now, pendingDriving, Integer.parseInt(row.to),
                plan.minuteMillis(row.end), "driving-arrived");
        }

        if (activeTransfer >= 0) {
            pendingRow = -1;
            return transferSnapshot(now, activeTransfer);
        }

        // Station presence is an obligation. Follow the next assigned train
        // only if no intervening transfer row has to be performed first.
        if (activeStation >= 0) {
            int following = nextOperationalRow(activeStation);
            if (following >= 0) {
                CorporateServicePlan.Segment row = rows.get(following);
                if (row.driving()) return pickupSnapshot(now, following);
                return transferSnapshot(now, following, activeStation);
            }
            return idleSnapshot(now, activeStation, Integer.parseInt(rows.get(activeStation).from),
                plan.minuteMillis(rows.get(activeStation).end), "station");
        }

        // Before the first movement (or between non-overlapping pieces), show
        // the time until the next scheduled obligation. No train is followed
        // merely because one happens to be passing now.
        if (next >= 0) {
            CorporateServicePlan.Segment row = rows.get(next);
            if (row.driving()) return pickupSnapshot(now, next);
            if ("TRA".equals(row.type)) return transferSnapshot(now, next);
            return idleSnapshot(now, next, Integer.parseInt(row.from),
                plan.minuteMillis(row.start), "next-activity");
        }

        // Preserve a late final pickup only if this tracker was already watching it.
        if (pendingRow >= 0 && !boarded[pendingRow] && !skipped[pendingRow]
            && now < plan.minuteMillis(rows.get(pendingRow).end) + FINAL_DELAY_GRACE_MS)
            return pickupSnapshot(now, pendingRow);
        if (previewOnly && lastDriving >= 0) return pickupSnapshot(now, lastDriving);
        return null;
    }

    private int nextOperationalRow(int after) {
        for (int i = after + 1; i < rows.size(); i++) {
            CorporateServicePlan.Segment row = rows.get(i);
            if (row.driving() || "TRA".equals(row.type)) return i;
        }
        return -1;
    }

    private Snapshot drivingSnapshot(long now, int index) {
        CorporateServicePlan.Segment row = rows.get(index);
        boolean driving = boarded[index] && !drivingArrived[index];
        if (!driving && !skipped[index] && !boarded[index]) pendingRow = index;
        else if (pendingRow == index || driving) pendingRow = -1;
        if (driving) pendingDriving = index;
        int photoRow = driving ? index : waitingRow(index);
        if (manualCheckpointActive(now)) photoRow = selectedRow;
        CorporateServicePlan.Selection selection = plan.selectionFor(index, driving);
        return new Snapshot(photoRow, index, !driving, driving, row.train, row.from,
            plan.minuteMillis(row.start), previewOnly ? "preview"
                : manualCheckpointActive(now) ? "manual-row"
                    : driving ? evidence[index] : "waiting", previewOnly, selection);
    }

    private Snapshot pickupSnapshot(long now, int index) {
        CorporateServicePlan.Segment row = rows.get(index);
        if (!boarded[index] && !skipped[index]) pendingRow = index;
        else if (pendingRow == index) pendingRow = -1;
        CorporateServicePlan.Selection selection = plan.selectionFor(index, false);
        int photoRow = manualCheckpointActive(now)
            ? selectedRow : waitingRow(index);
        return new Snapshot(photoRow, index, true, false, row.train, row.from,
            plan.minuteMillis(row.start), previewOnly ? "preview" : "waiting", previewOnly, selection);
    }

    private Snapshot transferSnapshot(long now, int index) {
        return transferSnapshot(now, index, index);
    }

    private Snapshot transferSnapshot(long now, int index, int activityRow) {
        CorporateServicePlan.Segment row = rows.get(index);
        CorporateServicePlan.Selection selection = plan.transferSelection(index);
        boolean manual = manualCheckpointActive(now);
        if (plan.oppositeTransfer(index) && !(manual && selectedRow == index)) {
            CorporateServicePlan.Segment drive = rows.get(index + 1);
            selection = new CorporateServicePlan.Selection(drive.train, selection.origin, selection.via,
                false, plan.minuteMillis(drive.start), CorporateServicePlan.Selection.CONNECTION,
                index, selection.origin, selection.destination);
        }
        return new Snapshot(manual ? selectedRow : activityRow, index, true, false, "", row.from,
            selection.takeoverAt, manual ? "manual-row" : "transfer", previewOnly, selection);
    }

    private Snapshot idleSnapshot(long now, int row, int stop, long countdownAt, String source) {
        CorporateServicePlan.Selection selection = plan.idleAtStopSelection(row, stop, countdownAt);
        int photoRow = manualCheckpointActive(now) ? selectedRow : row;
        return new Snapshot(photoRow, row, false, false, "", Integer.toString(stop),
            countdownAt, source, previewOnly, selection);
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
        return observe(now, train, location, reportedTrack, unambiguous, -1);
    }

    boolean observe(long now, String train, String location, String reportedTrack,
                    boolean unambiguous, long departureAt) {
        Snapshot snapshot = at(now);
        if (previewOnly || snapshot == null || !unambiguous
            || !snapshot.train.equals(train)) {
            resetObservations();
            return false;
        }

        int index = snapshot.pickupRow;
        CorporateServicePlan.Segment row = rows.get(index);
        if (snapshot.selection.kind == CorporateServicePlan.Selection.TRANSFER_PICKUP) {
            CorporateServicePlan.Selection target = snapshot.selection;

            int direction = target.via == 1 ? 1 : -1;
            for (int stop = CorporateServicePlan.FIRST_STOP; stop <= CorporateServicePlan.LAST_STOP; stop++) {
                if (!sameStation(location, CorporateServicePlan.stationName(Integer.toString(stop)))) continue;
                boolean onOriginPlatform = stop == target.origin && CorporateServicePlan.terminal(stop);
                if (!onOriginPlatform && !sameTrack(reportedTrack, Integer.toString(target.via))) return false;
                int offset = (stop - target.origin) * direction;
                if (offset == 0 && CorporateProjection.project(now, now, target.takeoverAt).laps == 0) {
                    riding = true;
                    selectedRow = -1;
                    resetObservations();
                    return true;
                }
                if (offset <= 0) {
                    CorporateProjection.Result passage = CorporateProjection.project(departureAt, now, target.takeoverAt);
                    if (offset >= -1 && passage.timeMs >= 0 && passage.laps == 0
                        && Math.abs(passage.timeMs - now) <= 4 * 60_000L) approachSeenAt = now;
                    else approachSeenAt = -1;
                    return false;
                }
                if (approachSeenAt < 0 || now - approachSeenAt > 4 * 60_000L
                    || (target.destination - stop) * direction < 0) return false;
                riding = true;
                selectedRow = -1;
                resetObservations();
                return true;
            }
            return false;
        }
        if (snapshot.waiting) {
            if ((!riding && now < snapshot.plannedStart - EARLY_WINDOW_MS)
                || !sameStation(location, CorporateServicePlan.stationName(row.from))) {
                resetObservations();
                return false;
            }
            // At terminal pickups the platform can be shared. At intermediate
            // stations, require the exact service via before confirming boarding.
            boolean terminal = CorporateServicePlan.terminal(Integer.parseInt(row.from));
            if (!terminal && !sameTrack(reportedTrack, row.via)) {
                resetObservations();
                return false;
            }
        } else if (snapshot.driving) {
            if (now < plan.minuteMillis(row.end) - CorporateProjection.LAP_MS / 2
                || !sameStation(location, CorporateServicePlan.stationName(row.to))
                || (!CorporateServicePlan.terminal(Integer.parseInt(row.to))
                    && !sameTrack(reportedTrack,
                        CorporateServicePlan.validVia(row.out) ? row.out : row.via))) {
                resetObservations();
                return false;
            }
        } else {
            resetObservations();
            return false;
        }

        if (seenRow == index && lastSeenAt >= 0
            && now - lastSeenAt >= MIN_OBSERVATION_SPACING_MS
            && now - lastSeenAt <= OBSERVATION_TIMEOUT_MS) consecutive++;
        else consecutive = 1;
        seenRow = index;
        lastSeenAt = now;
        // This train has already been followed from the transfer origin.
        // A single fresh sighting on the exact intermediate pickup platform
        // confirms the handover, even during a short station dwell.
        boolean linkedPlatformArrival = snapshot.waiting && riding && linkedTransfer == index - 1
            && !CorporateServicePlan.terminal(Integer.parseInt(row.from));
        if (consecutive < 2 && !linkedPlatformArrival) return false;
        if (snapshot.waiting) {
            boarded[index] = true;
            linkedTransfer = -1;
            approachSeenAt = -1;
            riding = false;
            evidence[index] = "corporate";
            if (selectedRow >= 0 && selectedRow < index) selectedRow = -1;
            pendingDriving = index;
        } else {
            drivingArrived[index] = true;
            if (selectedRow == index) selectedRow = -1;
            if (pendingDriving == index) pendingDriving = -1;
        }
        resetObservations();
        return true;
    }

    boolean confirmBoarded(long now, int row) {
        Snapshot snapshot = at(now);
        if (snapshot == null || snapshot.pickupRow != row
            || now < snapshot.plannedStart - 30L * 60_000L) return false;
        if (!rows.get(row).driving() || snapshot.selection.kind == CorporateServicePlan.Selection.TRANSFER_PICKUP)
            return false;
        linkedTransfer = -1;
        riding = false;
        approachSeenAt = -1;
        boarded[row] = true;
        evidence[row] = "manual";
        if (selectedRow >= 0 && selectedRow < row) selectedRow = -1;
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
