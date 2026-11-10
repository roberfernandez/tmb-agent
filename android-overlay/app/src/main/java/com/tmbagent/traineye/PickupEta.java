package com.tmbagent.traineye;

import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Tracks a scheduled pickup using the assigned train's live passage through its origin. */
final class PickupEta {
    private static final long MATCH_WINDOW_MS = 30 * 60_000L;
    private static final long MAX_BOARD_AGE_WHILE_APPROACHING_MS = 120_000L;
    private static final long APPROACH_POSITION_MAX_AGE_MS = 15_000L;
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
        String inbound = inboundTrack(pickup);
        String track = reportedTrack(train);
        if (station == origin && (terminal || inbound.equals(track))) return confirm(pickup);
        if (!"1".equals(inbound) && !"2".equals(inbound)) return false;
        int distance = stopsUntil(origin, station, inbound);
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

    long predictedArrival(ServicePlan.Row pickup, ServiceAlerts boards, long now,
                          JSONObject liveTrain, long positionAgeMs) {
        if (pickup == null || !canShow(pickup, now) || completed.contains(key(pickup))) return 0;
        return liveArrival(boards, pickup.from, pickup.via, pickup.train, now, liveTrain, positionAgeMs);
    }

    static long liveArrival(ServiceAlerts boards, String stop, String via, String train, long now,
                            JSONObject liveTrain, long positionAgeMs) {
        long boardAge = now - boards.boardReceivedAt(stop);
        long maxBoardAge = ServiceAlerts.BOARD_FRESH_MS;
        String approachVia = "";
        if (boardAge >= maxBoardAge && boardAge < MAX_BOARD_AGE_WHILE_APPROACHING_MS
            && positionAgeMs >= 0 && positionAgeMs < APPROACH_POSITION_MAX_AGE_MS
            && liveTrain != null) {
            approachVia = inboundTrack(stop, via.isEmpty() ? reportedTrack(liveTrain) : via);
            if (isApproaching(stop, approachVia, liveTrain)) maxBoardAge = MAX_BOARD_AGE_WHILE_APPROACHING_MS;
        }

        boolean terminal = isTerminal(stop);
        long arrival = terminal || via.isEmpty()
            ? boards.predictedArrivalEitherTrack(stop, via, train, now, maxBoardAge)
            : boards.predictedArrival(stop, via, train, now, maxBoardAge);
        if (maxBoardAge > ServiceAlerts.BOARD_FRESH_MS && via.isEmpty() && !terminal
            && arrival != boards.predictedArrival(stop, approachVia, train, now, maxBoardAge)) return 0;
        return arrival > now && arrival < now + 3_600_000L ? arrival : 0;
    }

    private static boolean isApproaching(String stop, String inbound, JSONObject train) {
        if (!inbound.equals(reportedTrack(train))) return false;
        int station = ServiceAlerts.stopPosition(train.optString("estacion", ""));
        return station != 0 && stopsUntil(Integer.parseInt(stop), station, inbound) > 0;
    }

    private static String inboundTrack(ServicePlan.Row pickup) {
        return inboundTrack(pickup.from, pickup.via);
    }

    private static String inboundTrack(String stop, String via) {
        if ("413".equals(stop)) return "2";
        if ("434".equals(stop)) return "1";
        return via;
    }

    static boolean isTerminal(String stop) {
        return "413".equals(stop) || "434".equals(stop);
    }

    static int stopsUntil(int destination, int current, String via) {
        if ("1".equals(via)) return destination - current;
        if ("2".equals(via)) return current - destination;
        return 0;
    }

    static String reportedTrack(JSONObject train) {
        return train.optString("platformTrack", train.optString("track", ""));
    }

    boolean confirm(ServicePlan.Row pickup) { return completed.add(key(pickup)); }

    boolean nearPickup(ServicePlan.Row pickup, long now) { return now >= scheduled(pickup) - MATCH_WINDOW_MS; }

    Set<String> completedRows() { return new HashSet<>(completed); }

    private long scheduled(ServicePlan.Row pickup) { return plan.at(anchor, pickup.start); }

    private static String key(ServicePlan.Row row) { return Integer.toString(row.index); }
}
