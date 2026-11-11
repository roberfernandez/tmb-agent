package com.tmbagent.traineye;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Locale;

/** The numeric L4 service number shared by the timetable and Miralin aliases. */
final class TrainId {
    static String normalize(String value) {
        if (value == null) return "";
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.matches("[MP](?:0[1-9]|[1-9]\\d)")) return "4" + code.substring(1);
        return code.matches("4(?:0[1-9]|[1-9]\\d)") ? code : "";
    }

    static String fromBoard(JSONObject entry) {
        return normalize(entry.optString("trainCode", entry.optString("tren", entry.optString("id", ""))));
    }

    static JSONObject positions(JSONObject raw) {
        JSONObject result = new JSONObject();
        if (raw == null) return result;
        Iterator<String> keys = raw.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject position = raw.optJSONObject(key);
            if (position == null) continue;
            String code = normalize(key);
            if (code.isEmpty()) code = fromBoard(position);
            if (code.isEmpty() || (result.has(code) && !code.equals(key.trim()))) continue;
            try { result.put(code, position); }
            catch (JSONException ignored) {}
        }
        return result;
    }
}
