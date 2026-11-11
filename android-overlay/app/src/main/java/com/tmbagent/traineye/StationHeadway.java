package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Interval supplied in the public selected-station forecast rows, separate from map timers. */
final class StationHeadway {
    private static final long FRESH_MS = 60_000L;
    private static final class Board {
        final JSONObject data;
        final long receivedAt;
        Board(JSONObject data, long receivedAt) { this.data = data; this.receivedAt = receivedAt; }
    }
    private static final class Match {
        final JSONObject row;
        final String via;
        final double arrival;
        Match(JSONObject row, String via, double arrival) { this.row = row; this.via = via; this.arrival = arrival; }
    }
    private final Map<String, Board> boards = new HashMap<>();

    void clear() { boards.clear(); }

    void accept(String requestedStop, JSONObject raw, long now) {
        if (raw == null || !requestedStop.matches("41[3-9]|42[0-9]|43[0-4]")) return;
        JSONObject nested = raw.optJSONObject("data");
        JSONObject data = nested == null ? raw : nested;
        if (!"L4".equals(data.optString("line", raw.optString("line", "L4")))) return;
        String declared = ServiceAlerts.boardStop(raw);
        if (!declared.isEmpty() && !requestedStop.equals(declared)) return;
        Board old = boards.get(requestedStop);
        if (old != null && now < old.receivedAt) return;
        try { boards.put(requestedStop, new Board(new JSONObject(data.toString()), now)); }
        catch (Exception ignored) { /* Keep the previous snapshot at its original age. */ }
    }

    private Match match(String stop, String via, String train, long now) {
        Board board = boards.get(stop);
        if (board == null || now < board.receivedAt || now - board.receivedAt >= FRESH_MS) return null;
        String normalized = TrainId.normalize(train);
        if (normalized.isEmpty()) return null;
        boolean restrict = !PickupEta.isTerminal(stop) && ("1".equals(via) || "2".equals(via));
        Match best = null;
        for (String side : new String[]{"1", "2"}) {
            if (restrict && !side.equals(via)) continue;
            JSONArray rows = board.data.optJSONArray("via" + side);
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !normalized.equals(TrainId.fromBoard(row))) continue;
                double arrival = row.optDouble("arrivalTime", Double.NaN);
                if (!Double.isFinite(arrival)) {
                    try { arrival = Instant.parse(row.optString("arrivalTime")).toEpochMilli(); }
                    catch (Exception ignored) { arrival = Double.NaN; }
                }
                if (!Double.isFinite(arrival)) {
                    double seconds = row.optDouble("secsLeft", Double.NaN);
                    arrival = Double.isFinite(seconds) ? board.receivedAt + seconds * 1000 : Double.POSITIVE_INFINITY;
                }
                if (best == null || arrival < best.arrival) best = new Match(row, side, arrival);
            }
        }
        return best;
    }

    long seconds(String stop, String via, String train, long now) {
        Match match = match(stop, via, train, now);
        if (match == null) return -1;
        double seconds = match.row.optDouble("secPreviousTrain", Double.NaN);
        return Double.isFinite(seconds) && seconds >= 0 ? Math.round(seconds) : -1;
    }

    String source(String stop, String via, String train, long now) {
        Match match = match(stop, via, train, now);
        Board board = boards.get(stop);
        String source = "miralin.cat stopDataUpdate." + stop;
        if (board == null) return "missing: " + source;
        if (now < board.receivedAt || now - board.receivedAt >= FRESH_MS) return "stale: " + source;
        return match == null ? "missing train: " + source : source + ".via" + match.via;
    }

    String label(String stop, String via, String train, long now) {
        long seconds = seconds(stop, via, train, now);
        if (seconds < 0) return "--";
        return seconds >= 3600
            ? String.format(Locale.ROOT, "%dh%02d", seconds / 3600, seconds % 3600 / 60)
            : String.format(Locale.ROOT, "%d′%02d″", seconds / 60, seconds % 60);
    }
}
