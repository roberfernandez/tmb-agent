package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** T.ANT is a measured interval, not an arrival countdown or a line distance. */
final class TrainHeadway {
    static final long FRESH_MS = 60_000L;
    private static final String FIELD = "secPreviousTrain";

    private static final class Sample {
        final long seconds, receivedAt;
        Sample(long seconds, long receivedAt) {
            this.seconds = seconds;
            this.receivedAt = receivedAt;
        }
    }

    private final Map<String, Sample> samples = new HashMap<>();

    void clear() { samples.clear(); }

    void acceptMap(JSONObject map, long now) {
        if (map == null) return;
        for (Iterator<String> keys = map.keys(); keys.hasNext();) {
            String key = keys.next();
            JSONObject entry = map.optJSONObject(key);
            if (entry == null) continue;
            String train = TrainId.normalize(key);
            if (train.isEmpty()) train = TrainId.fromBoard(entry);
            accept(train, entry, now);
        }
    }

    void acceptBoard(JSONObject raw, long now) {
        if (raw == null) return;
        JSONObject nested = raw.optJSONObject("data");
        JSONObject data = nested == null ? raw : nested;
        Set<String> seen = new HashSet<>();
        // lineTrains gives the current interval. A forecast row can carry the
        // same field, but may not replace an explicit null from the current list.
        acceptEntries(data.optJSONArray("lineTrains"), now, seen);
        acceptEntries(data.optJSONArray("via1"), now, seen);
        acceptEntries(data.optJSONArray("via2"), now, seen);
    }

    private void acceptEntries(JSONArray entries, long now, Set<String> seen) {
        if (entries == null) return;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || !entry.has(FIELD)) continue;
            String train = TrainId.fromBoard(entry);
            if (train.isEmpty() || !seen.add(train)) continue;
            accept(train, entry, now);
        }
    }

    private void accept(String train, JSONObject entry, long now) {
        if (train.isEmpty() || !entry.has(FIELD)) return;
        Sample previous = samples.get(train);
        if (previous != null && now < previous.receivedAt) return;
        double value = entry.optDouble(FIELD, Double.NaN);
        long seconds = Double.isFinite(value) && value >= 0 ? Math.round(value) : -1;
        samples.put(train, new Sample(seconds, now));
    }

    long seconds(String train, long now) {
        Sample sample = samples.get(TrainId.normalize(train));
        return sample == null || now < sample.receivedAt || now - sample.receivedAt >= FRESH_MS
            ? -1 : sample.seconds;
    }

    String label(String train, long now) {
        long seconds = seconds(train, now);
        if (seconds < 0) return "--";
        return seconds >= 3600
            ? String.format(Locale.ROOT, "%dh%02d", seconds / 3600, seconds % 3600 / 60)
            : String.format(Locale.ROOT, "%d′%02d″", seconds / 60, seconds % 60);
    }
}
