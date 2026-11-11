package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks the assigned passage and the full circuits that precede it. */
final class PickupEta {
    static final long LAP_MS = 77 * 60_000L;
    private static final long MATCH_WINDOW_MS = 30 * 60_000L;
    private static final long MAX_APPROACH_GAP_MS = 10 * 60_000L;
    private static final long MAX_BOARD_AGE_WHILE_APPROACHING_MS = 120_000L;
    private static final long APPROACH_POSITION_MAX_AGE_MS = 15_000L;

    static final class Estimate {
        static final Estimate NONE = new Estimate(0, false);
        final long arrival;
        final boolean approximate;

        Estimate(long arrival, boolean approximate) {
            this.arrival = arrival;
            this.approximate = approximate;
        }
    }

    private static final class LapWatch {
        int remaining;
        long nextPass;
        long lastPass;
        long mapAt;
        long approachAt;
        boolean atStation;
        boolean projected;
        boolean uncertain;
    }

    private final ServicePlan plan;
    private final long anchor;
    private final Set<String> completed;
    private final Map<Integer, LapWatch> watches = new HashMap<>();
    private int waiting = -1;
    private int approaching = -1;
    private boolean dirty;

    PickupEta(ServicePlan plan, long now, Set<String> completed) {
        this.plan = plan;
        this.anchor = plan.anchorAt(now);
        this.completed = new HashSet<>(completed);
    }

    ServicePlan.Row target(long now, String train) {
        train = TrainId.normalize(train);
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
        } else if ("TRA".equals(active.type) && next != null && active.to.equals(next.from)) {
            pickup = next;
        } else {
            return null;
        }

        if (!isPickup(pickup) || !pickup.train.equals(train) || isComplete(pickup)) return null;
        if (minute < pickup.start) waiting = pickup.index;
        return canShow(pickup, now) || now <= scheduled(pickup) + MATCH_WINDOW_MS ? pickup : null;
    }

    boolean isPickup(ServicePlan.Row row) {
        if (row == null || !row.driving() || !row.from.matches("41[3-9]|42[0-9]|43[0-4]")
            || !("1".equals(row.via) || "2".equals(row.via))) return false;
        if (row.index == 0) return true;
        ServicePlan.Row previous = plan.rows.get(row.index - 1);
        return !(previous.driving() && previous.end == row.start
            && previous.train.equals(row.train) && previous.to.equals(row.from));
    }

    boolean canShow(ServicePlan.Row pickup, long now) {
        return now < scheduled(pickup) || waiting == pickup.index || approaching == pickup.index
            || isTracking(pickup);
    }

    boolean observe(ServicePlan.Row pickup, JSONObject train, long now) {
        if (!isPickup(pickup) || train == null || isComplete(pickup)) return false;
        int station = ServiceAlerts.stopPosition(train.optString("estacion", ""));
        if (station == 0) return false;
        String track = reportedTrack(train);
        String inbound = inboundTrack(pickup);
        int origin = Integer.parseInt(pickup.from);
        int distance = stopsUntil(origin, station, inbound);
        boolean onApproach = inbound.equals(track) && distance > 0 && distance <= 3;
        LapWatch watch = watches.get(pickup.index);
        if (watch == null) {
            if (onApproach && nearPickup(pickup, now)) approaching = pickup.index;
            return false;
        }
        if (now < watch.mapAt) return false;
        watch.mapAt = now;

        boolean atStation = station == origin && (isTerminal(pickup.from) || inbound.equals(track));
        boolean hadApproach = watch.approachAt > 0 && now - watch.approachAt <= MAX_APPROACH_GAP_MS;
        boolean passedBetweenFrames = hadApproach && (inbound.equals(track) && distance < 0
            || isTerminal(pickup.from) && !inbound.equals(track) && validTrack(track) && distance <= 3);
        boolean passage = atStation && !watch.atStation || passedBetweenFrames;
        if (watch.atStation != atStation) dirty = true;
        watch.atStation = atStation;
        if (onApproach) {
            if (watch.approachAt / 60_000L != now / 60_000L) dirty = true;
            watch.approachAt = now;
        } else if (validTrack(track) || atStation) {
            watch.approachAt = 0;
        }
        if (!passage || Math.abs(now - watch.nextPass) > LAP_MS / 2
            || (watch.lastPass > 0 && now - watch.lastPass < LAP_MS / 2)) return false;

        if (watch.remaining == 0) return confirm(pickup);
        watch.remaining--;
        watch.lastPass = now;
        watch.nextPass = now + LAP_MS;
        watch.projected = true;
        watch.uncertain = false;
        dirty = true;
        return false;
    }

    long predictedArrival(ServicePlan.Row pickup, ServiceAlerts boards, long now,
                          JSONObject liveTrain, long positionAgeMs) {
        return displayed(pickup, boards, now, liveTrain, positionAgeMs).arrival;
    }

    Estimate displayed(ServicePlan.Row pickup, ServiceAlerts boards, long now,
                       JSONObject liveTrain, long positionAgeMs) {
        if (pickup == null || !canShow(pickup, now)) return Estimate.NONE;
        return estimate(pickup, boards, now, liveTrain, positionAgeMs, true);
    }

    Estimate estimate(ServicePlan.Row pickup, ServiceAlerts boards, long now,
                      JSONObject liveTrain, long positionAgeMs, boolean retain) {
        if (!isPickup(pickup) || isComplete(pickup)) return Estimate.NONE;
        long boardAt = boards.boardReceivedAt(pickup.from);
        long age = now - boardAt;
        boolean fresh = age >= 0 && age < ServiceAlerts.BOARD_FRESH_MS;
        LapWatch watch = watches.get(pickup.index);
        boolean retained = retain && watch != null && !fresh && age < MAX_BOARD_AGE_WHILE_APPROACHING_MS
            && liveTrain != null && positionAgeMs >= 0 && positionAgeMs < APPROACH_POSITION_MAX_AGE_MS
            && isApproaching(pickup.from, inboundTrack(pickup), liveTrain);
        long next = 0;
        int remaining = watch == null ? 0 : watch.remaining;
        long reference = watch == null ? scheduled(pickup) : watch.nextPass;
        long bestDifference = watch == null ? MATCH_WINDOW_MS : LAP_MS / 2;
        boolean hasCandidate = false;
        if (fresh || retained) {
            long maxAge = retained ? MAX_BOARD_AGE_WHILE_APPROACHING_MS : ServiceAlerts.BOARD_FRESH_MS;
            for (long arrival : boards.pickupArrivals(pickup, now, maxAge)) {
                if (arrival <= now || arrival > now + LAP_MS + MATCH_WINDOW_MS) continue;
                if (watch != null && watch.lastPass > 0
                    && (boardAt < watch.lastPass || arrival - watch.lastPass < LAP_MS / 2)) continue;
                hasCandidate = true;
                int circuits = watch == null
                    ? (int) Math.max(0, Math.round((reference - arrival) / (double) LAP_MS)) : 0;
                long difference = Math.abs(arrival + circuits * LAP_MS - reference);
                if (difference > bestDifference || (next != 0 && difference == bestDifference && arrival >= next)) continue;
                next = arrival;
                bestDifference = difference;
                if (watch == null) remaining = circuits;
            }
        }
        if (next == 0) {
            if (watch != null && fresh && hasCandidate && !watch.uncertain) {
                watch.uncertain = true;
                dirty = true;
            }
            if (retain && watch != null && watch.projected && !watch.uncertain
                && watch.nextPass > now) return new Estimate(watch.nextPass + watch.remaining * LAP_MS, true);
            return Estimate.NONE;
        }

        if (watch == null) {
            watch = new LapWatch();
            watch.remaining = remaining;
            if (liveTrain != null && positionAgeMs >= 0 && positionAgeMs < APPROACH_POSITION_MAX_AGE_MS) {
                int station = ServiceAlerts.stopPosition(liveTrain.optString("estacion", ""));
                int destination = Integer.parseInt(pickup.from);
                String inbound = inboundTrack(pickup);
                boolean rightDirection = inbound.equals(reportedTrack(liveTrain));
                int distance = stopsUntil(destination, station, inbound);
                if (station == destination && next - now > 3 * 60_000L) {
                    watch.atStation = isTerminal(pickup.from) || rightDirection;
                } else if (station != 0 && rightDirection && distance > 0 && distance <= 3) {
                    watch.approachAt = now - positionAgeMs;
                }
            }
            watches.put(pickup.index, watch);
            dirty = true;
        }
        if (fresh) {
            if (watch.projected || watch.uncertain || watch.nextPass / 60_000L != next / 60_000L) dirty = true;
            watch.nextPass = next;
            watch.projected = false;
            watch.uncertain = false;
        }
        return new Estimate(next + watch.remaining * LAP_MS, retained || watch.remaining > 0);
    }

    boolean isTracking(ServicePlan.Row pickup) {
        return watches.containsKey(pickup.index) && !isComplete(pickup);
    }

    int remainingLaps(ServicePlan.Row pickup) {
        LapWatch watch = watches.get(pickup.index);
        return watch == null || watch.uncertain ? -1 : watch.remaining;
    }

    void restoreState(String value) {
        try {
            JSONArray saved = new JSONArray(value);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject state = saved.getJSONObject(i);
                int index = state.getInt("row");
                if (index < 0 || index >= plan.rows.size() || isComplete(plan.rows.get(index))) continue;
                LapWatch watch = new LapWatch();
                watch.remaining = state.getInt("remaining");
                watch.nextPass = state.getLong("next");
                watch.lastPass = state.optLong("last");
                watch.approachAt = state.optLong("approach");
                watch.atStation = state.optBoolean("atStation");
                watch.projected = state.optBoolean("projected");
                watch.uncertain = state.optBoolean("uncertain");
                if (watch.remaining >= 0 && watch.nextPass > 0) watches.put(index, watch);
            }
        } catch (JSONException ignored) {}
    }

    String changedState() {
        if (!dirty) return null;
        JSONArray saved = new JSONArray();
        for (Map.Entry<Integer, LapWatch> entry : watches.entrySet()) {
            LapWatch watch = entry.getValue();
            try {
                saved.put(new JSONObject().put("row", entry.getKey()).put("remaining", watch.remaining)
                    .put("next", watch.nextPass).put("last", watch.lastPass).put("approach", watch.approachAt)
                    .put("atStation", watch.atStation).put("projected", watch.projected).put("uncertain", watch.uncertain));
            } catch (JSONException ignored) {}
        }
        dirty = false;
        return saved.toString();
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
        String nextVia = via;
        if (via.isEmpty()) {
            if (terminal) {
                nextVia = inboundTrack(stop, "");
            } else if (maxBoardAge == ServiceAlerts.BOARD_FRESH_MS && liveTrain != null
                && positionAgeMs >= 0 && positionAgeMs < 45_000L) {
                nextVia = nextPassTrack(stop, liveTrain);
            }
        }
        long arrival = nextVia.isEmpty()
            ? boards.predictedArrivalEitherTrack(stop, "", train, now, maxBoardAge)
            : boards.predictedArrival(stop, nextVia, train, now, maxBoardAge);
        if (maxBoardAge > ServiceAlerts.BOARD_FRESH_MS && via.isEmpty() && !terminal
            && arrival != boards.predictedArrival(stop, approachVia, train, now, maxBoardAge)) return 0;
        return arrival > now && arrival < now + LAP_MS + MATCH_WINDOW_MS ? arrival : 0;
    }

    private static String nextPassTrack(String stop, JSONObject train) {
        int station = ServiceAlerts.stopPosition(train.optString("estacion", ""));
        String currentVia = reportedTrack(train);
        if (station == 0 || !validTrack(currentVia)) return "";
        int distance = stopsUntil(Integer.parseInt(stop), station, currentVia);
        if (distance > 0) return currentVia;
        if (distance < 0) return "1".equals(currentVia) ? "2" : "1";
        return "";
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

    boolean confirm(ServicePlan.Row pickup) {
        if (!completed.add(key(pickup))) return false;
        watches.remove(pickup.index);
        dirty = true;
        return true;
    }

    boolean isComplete(ServicePlan.Row pickup) { return completed.contains(key(pickup)); }

    boolean nearPickup(ServicePlan.Row pickup, long now) {
        return now >= scheduled(pickup) - MATCH_WINDOW_MS;
    }

    Set<String> completedRows() { return new HashSet<>(completed); }

    private static boolean validTrack(String track) { return "1".equals(track) || "2".equals(track); }

    private long scheduled(ServicePlan.Row pickup) { return plan.at(anchor, pickup.start); }

    private static String key(ServicePlan.Row row) { return Integer.toString(row.index); }
}
