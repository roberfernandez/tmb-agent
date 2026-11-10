package com.tmbagent.traineye;

import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Tracks a scheduled pickup using the assigned train's live passage through its origin. */
final class PickupEta {
    private static final long MATCH_WINDOW_MS = 30 * 60_000L;
    private final ServicePlan plan;
    private final long anchor;
    private final Set<String> completed;
    private int waiting = -1;
    private int approaching = -1;

    PickupEta(ServicePlan plan, long now, Set<String> completed) {
        this.plan = plan;
        this.anchor = plan.anchorAt(now);
        this.completed = new HashSet<>(completed);
    }

    ServicePlan.Row target(long now, String train) {
        int minute = plan.minutesAt(now);
        ServicePlan.Row active = null;
        ServicePlan.Row next = null;
        for (ServicePlan.Row row : plan.rows) {
            if (minute >= row.start && minute < row.end && active == null) active = row;
            if (minute < row.start && (next == null || row.start < next.start)) next = row;
        }

        ServicePlan.Row pickup;
        if (active == null) {
            pickup = next;
        } else if (active.driving()) {
            pickup = active;
        } else if ("EST".equals(active.type)) {
            pickup = null;
            for (ServicePlan.Row row : plan.rows) {
                if (row.start < active.end || "EST".equals(row.type)) continue;
                if (pickup == null || row.start < pickup.start) pickup = row;
            }
            if (pickup == null || !active.from.equals(pickup.from)) return null;
        } else {
            return null;
        }

        if (pickup == null || !pickup.driving() || !pickup.train.equals(train)
            || !pickup.from.matches("41[3-9]|42[0-9]|43[0-4]") || completed.contains(key(pickup))) return null;
        if (pickup.index > 0) {
            ServicePlan.Row previous = plan.rows.get(pickup.index - 1);
            if (previous.driving() && previous.end == pickup.start
                && previous.train.equals(pickup.train) && previous.to.equals(pickup.from)) return null;
        }
        if (minute < pickup.start) waiting = pickup.index;
        if (canShow(pickup, now) || now <= scheduled(pickup) + MATCH_WINDOW_MS) return pickup;
        return null;
    }

    boolean canShow(ServicePlan.Row pickup, long now) {
        return now < scheduled(pickup) || waiting == pickup.index || approaching == pickup.index;
    }

    boolean observe(ServicePlan.Row pickup, JSONObject train, long now) {
        if (pickup == null || train == null || now < scheduled(pickup) - MATCH_WINDOW_MS) return false;
        int station = ServiceAlerts.stopPosition(train.optString("estacion", ""));
        if (station == 0) return false;
        int origin = Integer.parseInt(pickup.from);
        boolean terminal = origin == 413 || origin == 434;
        String inbound = origin == 413 ? "2" : origin == 434 ? "1" : pickup.via;
        String track = train.optString("platformTrack", train.optString("track", ""));
        if (station == origin && (terminal || inbound.equals(track))) return confirm(pickup);
        if (!"1".equals(inbound) && !"2".equals(inbound)) return false;
        int distance = (origin - station) * ("1".equals(inbound) ? 1 : -1);
        if (inbound.equals(track)) {
            if (distance < 0 && approaching == pickup.index) return confirm(pickup);
            if (distance > 0 && distance <= 3) approaching = pickup.index;
        } else if (terminal && approaching == pickup.index && distance <= 3
            && ("1".equals(track) || "2".equals(track))) {
            // At a terminus the train can turn around between two map frames.
            return confirm(pickup);
        }
        return false;
    }

    boolean confirm(ServicePlan.Row pickup) { return completed.add(key(pickup)); }

    boolean nearPickup(ServicePlan.Row pickup, long now) { return now >= scheduled(pickup) - MATCH_WINDOW_MS; }

    Set<String> completedRows() { return new HashSet<>(completed); }

    private long scheduled(ServicePlan.Row pickup) { return plan.at(anchor, pickup.start); }

    private static String key(ServicePlan.Row row) { return Integer.toString(row.index); }
}
