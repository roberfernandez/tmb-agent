package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded in-memory observations. Never sends data or changes subscription state. */
final class SocketDiagnostics {
    private static final int HISTORY_LIMIT = 24;
    private static final int RAW_LIMIT = 65_536;
    private final ArrayDeque<JSONObject> history = new ArrayDeque<>();
    private final Map<String, Integer> events = new LinkedHashMap<>();
    private final Map<String, JSONObject> active = new LinkedHashMap<>();
    private JSONObject lastBoard;
    private int sessions, requests, boardFrames, acceptedBoards, rejectedBoards;

    synchronized void session(long now) {
        sessions++;
        add("session", "Session " + sessions, now);
    }

    synchronized void event(String name) {
        String key = name.length() > 80 ? name.substring(0, 80) : name;
        if (events.size() >= 24 && !events.containsKey(key)) key = "other";
        events.put(key, events.getOrDefault(key, 0) + 1);
    }

    synchronized void requestStarted(String phase, String body, long now) {
        String method = body == null ? "GET" : "POST";
        try {
            JSONObject request = new JSONObject().put("phase", phase).put("startedAtMs", now);
            if (body != null) request.put("packet", body.length() <= 1024 ? body : body.substring(0, 1024));
            active.put(method, request);
        } catch (JSONException ignored) {}
    }

    synchronized void requestFinished(String phase, String body, String error, long now) {
        String method = body == null ? "GET" : "POST";
        JSONObject request = active.remove(method);
        requests++;
        try {
            JSONObject item = new JSONObject().put("kind", "request").put("method", method)
                .put("phase", phase).put("atMs", now).put("error", error);
            if (request != null) {
                item.put("durationMs", Math.max(0, now - request.optLong("startedAtMs")));
                if (request.has("packet")) item.put("packet", request.opt("packet"));
            }
            if (body != null || !error.isEmpty()) append(item);
        } catch (JSONException ignored) {}
    }

    synchronized void board(String stop, JSONObject raw, String decision, long now) {
        boardFrames++;
        if ("accepted".equals(decision)) acceptedBoards++; else rejectedBoards++;
        try {
            String json = raw.toString();
            lastBoard = new JSONObject().put("stop", stop == null ? "" : stop)
                .put("decision", decision).put("receivedAtMs", now)
                .put("rawSize", json.length());
            if (json.length() <= RAW_LIMIT) lastBoard.put("raw", new JSONObject(json));
            else lastBoard.put("rawTruncated", true).put("rawPrefix", json.substring(0, RAW_LIMIT));
            if (!"accepted".equals(decision)) add("board-rejected", decision, now);
        } catch (JSONException ignored) {}
    }

    synchronized JSONObject snapshot(long now) {
        try {
            JSONObject counts = new JSONObject();
            for (Map.Entry<String, Integer> entry : events.entrySet()) counts.put(entry.getKey(), entry.getValue());
            JSONObject running = new JSONObject();
            for (Map.Entry<String, JSONObject> entry : active.entrySet()) {
                JSONObject request = new JSONObject(entry.getValue().toString());
                request.put("ageMs", Math.max(0, now - request.optLong("startedAtMs")));
                running.put(entry.getKey(), request);
            }
            JSONArray recent = new JSONArray();
            for (JSONObject item : history) recent.put(new JSONObject(item.toString()));
            JSONObject result = new JSONObject().put("sessionCount", sessions).put("completedRequests", requests)
                .put("events", counts).put("boardFrames", boardFrames)
                .put("acceptedBoards", acceptedBoards).put("rejectedBoards", rejectedBoards)
                .put("activeRequests", running).put("history", recent);
            if (lastBoard != null) {
                JSONObject board = new JSONObject(lastBoard.toString());
                board.put("ageMs", Math.max(0, now - board.optLong("receivedAtMs")));
                result.put("lastBoardFrame", board);
            }
            return result;
        } catch (JSONException ignored) { return new JSONObject(); }
    }

    private void add(String kind, String detail, long now) {
        try { append(new JSONObject().put("kind", kind).put("detail", detail).put("atMs", now)); }
        catch (JSONException ignored) {}
    }

    private void append(JSONObject item) {
        if (history.size() >= HISTORY_LIMIT) history.removeFirst();
        history.addLast(item);
    }
}
