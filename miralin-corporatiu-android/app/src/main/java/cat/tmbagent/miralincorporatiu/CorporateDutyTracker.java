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
    private final String[] transferTrains;
    private final boolean[] transferArrived;
    private int transferSeenRow = -1;
    private long transferLastSeenAt = -1;
    private int transferConsecutive;
    private int pendingTransfer = -1;
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
        long end = plan.minuteMillis(row.end);
        long first = plan.minuteMillis(rows.get(0).start);
        long last = plan.minuteMillis(rows.get(rows.size() - 1).end);
        // The user may still be on an earlier station or transfer row because
        // of a severe delay. Only a driving-row claim needs a tight time check.
        // If the worker explicitly chooses an out-of-time row, the app becomes
        // a clearly labeled TEST instead of refusing the selection. Stop work
        // alerts and automatic pickup claims for this experimental service.
        if (now < first - FINAL_DELAY_GRACE_MS
            || now > last + FINAL_DELAY_GRACE_MS
            || (row.driving() && (now < start - 30L * 60_000L
                || now > end + FINAL_DELAY_GRACE_MS)))
            previewOnly = true;
        // If the worker explicitly selects a later stage, earlier missed
        // pickups must not hold the watcher on the wrong train.
        for (int i = 0; i < index; i++)
            if (rows.get(i).driving() && !boarded[i]) skipped[i] = true;
        if (pendingRow >= 0 && pendingRow < index) pendingRow = -1;
        if (row.driving()) {
            boarded[index] = true;
            evidence[index] = "manual";
        }
        selectedRow = index;
        selectedAt = now;
        resetObservations();
        return true;
    }

    private boolean manualCheckpointActive(long now) {
        if (selectedRow < 0) return false;
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

    private int nextDrivingFrom(int row) {
        for (int i = row; i < rows.size(); i++)
            if (rows.get(i).driving()) return i;
        return -1;
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
        this.transferTrains = new String[rows.size()];
        this.transferArrived = new boolean[rows.size()];
        this.drivingArrived = new boolean[rows.size()];
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
        if (now >= serviceEnd + FINAL_DELAY_GRACE_MS && !previewOnly) return null;

        // A real driving piece always wins over a station or transfer piece
        // that overlaps it. A delayed pickup observed by this running tracker
        // also remains authoritative until the train is actually boarded.
        int focus = activeCon;
        if (pendingRow >= 0 && pendingRow < rows.size()
            && !boarded[pendingRow] && !skipped[pendingRow]) focus = pendingRow;
        if (focus >= 0) {
            if (drivingArrived[focus]) {
                CorporateServicePlan.Segment row = rows.get(focus);
                return idleSnapshot(focus, Integer.parseInt(row.to),
                    plan.minuteMillis(row.end), "driving-arrived");
            }
            return drivingSnapshot(now, focus);
        }
        if (pendingDriving >= 0 && !drivingArrived[pendingDriving])
            return drivingSnapshot(now, pendingDriving);
        if (pendingDriving >= 0 && drivingArrived[pendingDriving]
            && now < plan.minuteMillis(rows.get(pendingDriving).end)) {
            CorporateServicePlan.Segment row = rows.get(pendingDriving);
            return idleSnapshot(pendingDriving, Integer.parseInt(row.to),
                plan.minuteMillis(row.end), "driving-arrived");
        }

        // A transfer is a movement obligation. Once it starts, keep following
        // a suitable train until it is confirmed at the destination, even if
        // the scheduled transfer end has passed because of a delay.
        if (activeTransfer >= 0 && !transferArrived[activeTransfer]) pendingTransfer = activeTransfer;
        if (pendingTransfer >= 0 && !transferArrived[pendingTransfer]) {
            return transferSnapshot(now, pendingTransfer, true);
        }

        // If the transfer train arrived early, stay at its destination until
        // the scheduled station-duty start. Do not use that station period as
        // extra transfer time or chase another train.
        if (activeTransfer >= 0 && transferArrived[activeTransfer]) {
            CorporateServicePlan.Segment transfer = rows.get(activeTransfer);
            return idleSnapshot(activeTransfer, Integer.parseInt(transfer.to),
                plan.minuteMillis(transfer.end), "transfer-arrived");
        }

        // Station presence is an obligation. Follow the next assigned train
        // only if no intervening transfer row has to be performed first.
        if (activeStation >= 0) {
            int following = nextOperationalRow(activeStation);
            if (following >= 0) {
                CorporateServicePlan.Segment row = rows.get(following);
                if (row.driving()) return pickupSnapshot(following);
                return idleSnapshot(following, Integer.parseInt(row.from),
                    plan.minuteMillis(row.start), "station-before-transfer");
            }
            return idleSnapshot(activeStation, Integer.parseInt(rows.get(activeStation).from),
                plan.minuteMillis(rows.get(activeStation).end), "station");
        }

        // A completed transfer can remain current after its scheduled end if
        // the arrival was late; the next active station/CON row then takes over.
        if (activeTransfer >= 0 && transferArrived[activeTransfer]) {
            int following = nextOperationalRow(activeTransfer);
            if (following >= 0 && rows.get(following).driving()) return pickupSnapshot(following);
            if (following >= 0) {
                CorporateServicePlan.Segment row = rows.get(following);
                return idleSnapshot(following, Integer.parseInt(row.from),
                    plan.minuteMillis(row.start), "next-activity");
            }
        }

        // Before the first movement (or between non-overlapping pieces), show
        // the time until the next scheduled obligation. No train is followed
        // merely because one happens to be passing now.
        if (next >= 0) {
            CorporateServicePlan.Segment row = rows.get(next);
            if (row.driving()) return pickupSnapshot(next);
            return idleSnapshot(next, Integer.parseInt(row.from),
                plan.minuteMillis(row.start), "next-activity");
        }

        // Preserve a late final pickup only if this tracker was already watching it.
        if (pendingRow >= 0 && !boarded[pendingRow] && !skipped[pendingRow]
            && now < plan.minuteMillis(rows.get(pendingRow).end) + FINAL_DELAY_GRACE_MS)
            return pickupSnapshot(pendingRow);
        if (previewOnly && lastDriving >= 0) return pickupSnapshot(lastDriving);
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
        int photoRow = driving ? Math.max(index, plan.viewerIndex(now)) : waitingRow(index);
        if (manualCheckpointActive(now)) photoRow = selectedRow;
        CorporateServicePlan.Selection selection = plan.selectionFor(index, driving);
        return new Snapshot(photoRow, index, !driving, driving, row.train, row.from,
            plan.minuteMillis(row.start), previewOnly ? "preview"
                : driving ? evidence[index] : "waiting", previewOnly, selection);
    }

    private Snapshot pickupSnapshot(int index) {
        CorporateServicePlan.Segment row = rows.get(index);
        CorporateServicePlan.Selection selection = plan.selectionFor(index, false);
        return new Snapshot(waitingRow(index), index, true, false, row.train, row.from,
            plan.minuteMillis(row.start), previewOnly ? "preview" : "waiting", previewOnly, selection);
    }

    private Snapshot transferSnapshot(long now, int index, boolean started) {
        CorporateServicePlan.Segment row = rows.get(index);
        CorporateServicePlan.Selection selection = plan.transferSelection(index,
            transferTrains[index], started);
        if (manualCheckpointActive(now)) {
            return new Snapshot(selectedRow, index, true, false, selection.train,
                row.from, plan.minuteMillis(row.start), "manual-row", previewOnly, selection);
        }
        return new Snapshot(index, index, selection.transferWaiting()
            || selection.transferSearching(), false, selection.train, row.from,
            plan.minuteMillis(row.start), selection.transferSearching() ? "transfer-search"
                : "transfer-track", previewOnly, selection);
    }

    private Snapshot idleSnapshot(int row, int stop, long countdownAt, String source) {
        CorporateServicePlan.Selection selection = plan.idleAtStopSelection(row, stop, countdownAt);
        return new Snapshot(row, row, false, false, "", Integer.toString(stop),
            countdownAt, source, previewOnly, selection);
    }

    boolean setTransferTrain(int row, String selectedTrain) {
        if (row < 0 || row >= rows.size() || !"TRA".equals(rows.get(row).type)
            || !CorporateServicePlan.validTrain(selectedTrain)) return false;
        transferTrains[row] = selectedTrain;
        transferSeenRow = row;
        transferLastSeenAt = -1;
        transferConsecutive = 0;
        return true;
    }

    boolean observeTransfer(long now, String selectedTrain, String location) {
        Snapshot snapshot = at(now);
        if (previewOnly || snapshot == null || snapshot.selection == null
            || snapshot.selection.kind != CorporateServicePlan.Selection.TRANSFER_TRACK
            || !snapshot.selection.train.equals(selectedTrain)
            || !sameStation(location, CorporateServicePlan.stationName(
                Integer.toString(snapshot.selection.destination)))) {
            transferSeenRow = -1;
            transferLastSeenAt = -1;
            transferConsecutive = 0;
            return false;
        }
        int row = snapshot.selection.row;
        if (transferSeenRow == row && transferLastSeenAt >= 0
            && now - transferLastSeenAt >= MIN_OBSERVATION_SPACING_MS
            && now - transferLastSeenAt <= OBSERVATION_TIMEOUT_MS) transferConsecutive++;
        else transferConsecutive = 1;
        transferSeenRow = row;
        transferLastSeenAt = now;
        if (transferConsecutive < 2) return false;
        transferArrived[row] = true;
        if (pendingTransfer == row) pendingTransfer = -1;
        transferSeenRow = -1;
        transferLastSeenAt = -1;
        transferConsecutive = 0;
        return true;
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
        if (previewOnly || snapshot == null || !unambiguous
            || !snapshot.train.equals(train)) {
            resetObservations();
            return false;
        }

        int index = snapshot.pickupRow;
        CorporateServicePlan.Segment row = rows.get(index);
        if (snapshot.waiting) {
            if (now < snapshot.plannedStart - EARLY_WINDOW_MS
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
            if (!sameStation(location, CorporateServicePlan.stationName(row.to))
                || !sameTrack(reportedTrack,
                    Integer.toString(CorporateServicePlan.directionVia(row.from, row.to)))) {
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
        if (consecutive < 2) return false;
        if (snapshot.waiting) {
            boarded[index] = true;
            evidence[index] = "corporate";
            if (selectedRow >= 0 && selectedRow < index) selectedRow = -1;
            pendingDriving = index;
        } else {
            drivingArrived[index] = true;
            if (pendingDriving == index) pendingDriving = -1;
        }
        resetObservations();
        return true;
    }

    boolean confirmBoarded(long now, int row) {
        Snapshot snapshot = at(now);
        if (snapshot == null || snapshot.pickupRow != row
            || now < snapshot.plannedStart - 30L * 60_000L) return false;
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
