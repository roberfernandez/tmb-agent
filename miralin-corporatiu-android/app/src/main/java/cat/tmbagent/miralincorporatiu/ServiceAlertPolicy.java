package cat.tmbagent.miralincorporatiu;

/** Pure alert rules shared by the service and regression tests. */
final class ServiceAlertPolicy {
    static final String TAG_PREFIX = "service-";
    static final int NOTIFICATION_ID = 9414;
    private static final long FRESH_MS = 60_000L;
    private static final long PASS_GRACE_MS = 60_000L;

    private ServiceAlertPolicy() {}

    static CorporateServicePlan.Notice liveTrainNotice(CorporateServicePlan plan,
            CorporateDutyTracker.Snapshot snapshot, CorporateTrainSelection.Train liveTrain,
            long receivedAt, long departureAt, long now, int marginMinutes) {
        if (plan == null || snapshot == null || liveTrain == null || !snapshot.waiting
            || snapshot.driving || snapshot.selection == null) return null;
        int kind = snapshot.selection.kind;
        if (kind != CorporateServicePlan.Selection.PICKUP
            && kind != CorporateServicePlan.Selection.TRANSFER_PICKUP) return null;
        if (receivedAt <= 0 || now - receivedAt > FRESH_MS || departureAt <= 0) return null;
        long margin = Math.max(1, Math.min(120, marginMinutes)) * 60_000L;
        if (now < departureAt - margin || now >= departureAt + PASS_GRACE_MS) return null;
        int row = snapshot.pickupRow;
        if (row < 0 || row >= plan.viewerSegments().size()) return null;
        CorporateServicePlan.Segment driving = plan.viewerSegments().get(row);
        if (!driving.driving() || !driving.train.equals(snapshot.train)) return null;

        String origin = CorporateServicePlan.stationName(driving.from);
        String destination = CorporateServicePlan.stationName(driving.to);
        boolean transferPickup = kind == CorporateServicePlan.Selection.TRANSFER_PICKUP;
        String route = transferPickup ? origin + " → " + destination
            : origin + (snapshot.selection.via > 0 ? " · Via " + snapshot.selection.via : "");
        String time = OfficialDepartureCountdown.clock(departureAt);
        String details = "Previsió de Miralín corporatiu " + time;
        return new CorporateServicePlan.Notice(liveTrainKey(row), "TREN " + snapshot.train,
            route, time, details, true);
    }

    static CorporateServicePlan.Notice confirmedArrival(CorporateServicePlan plan, int row,
            String train, String station, int destination, long now) {
        if (plan == null || row < 0 || row >= plan.viewerSegments().size()
            || train == null || station == null) return null;
        CorporateServicePlan.Segment driving = plan.viewerSegments().get(row);
        if (!driving.driving() || !driving.train.equals(train)) return null;
        String route = station + " → " + CorporateServicePlan.stationName(Integer.toString(destination));
        String time = OfficialDepartureCountdown.clock(now);
        return new CorporateServicePlan.Notice(liveTrainKey(row), "TREN " + train,
            route, time, "Arribada confirmada per Miralín corporatiu", true);
    }

    static String liveTrainKey(int row) { return "live-train-" + row; }

    static String connectionKey(int row) { return "live-connection-" + row; }

    static String tag(String planIdentity, String key) {
        return TAG_PREFIX + planIdentity + "-" + key;
    }

    static String keyFromTag(String planIdentity, String tag) {
        String prefix = TAG_PREFIX + planIdentity + "-";
        return tag != null && tag.startsWith(prefix) ? tag.substring(prefix.length()) : null;
    }

    static boolean isRelevant(String key, long now, boolean alertsEnabled,
            CorporateServicePlan plan, CorporateDutyTracker.Snapshot snapshot) {
        if (!alertsEnabled || plan == null || key == null) return false;
        if (key.startsWith("transfer-")) {
            int row = suffix(key, "transfer-");
            if (row < 0 || row >= plan.viewerSegments().size()) return false;
            CorporateServicePlan.Segment segment = plan.viewerSegments().get(row);
            return "TRA".equals(segment.type) && now < plan.minuteMillis(segment.end)
                && snapshot != null && snapshot.photoRow <= row;
        }
        if (key.startsWith("live-train-")) {
            int row = suffix(key, "live-train-");
            return snapshot != null && snapshot.waiting && !snapshot.driving
                && snapshot.pickupRow == row;
        }
        if (key.startsWith("live-connection-")) {
            int row = suffix(key, "live-connection-");
            return snapshot != null && snapshot.waiting && !snapshot.driving
                && snapshot.pickupRow == row;
        }
        return false;
    }

    private static int suffix(String key, String prefix) {
        try { return Integer.parseInt(key.substring(prefix.length())); }
        catch (RuntimeException ignored) { return -1; }
    }
}
