package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local, per-device advice. Never treats old station boards as live arrivals. */
final class ServiceAlerts {
    private static final long LIVE_MATCH_WINDOW = 15 * 60000L;
    private static final long TAKE_MATCH_WINDOW = 30 * 60000L;
    private static final long NOTICE_CHANGE = 2 * 60000L;
    private static final String RETURN_ALERT_PREFIX = "retorn-";

    static final class Alert {
        final String key, title, body;
        final long expiresAt;
        final int takeRow;
        Alert(String key, String title, String body) { this(key, title, body, Long.MAX_VALUE); }
        Alert(String key, String title, String body, long expiresAt) {
            this(key, title, body, expiresAt, -1);
        }
        Alert(String key, String title, String body, long expiresAt, int takeRow) {
            this.key = key; this.title = title; this.body = body; this.expiresAt = expiresAt;
            this.takeRow = takeRow;
        }
    }
    private static final class TakeWatch {
        int distance;
        long forecast, seenAt, announcedArrival;
        boolean rearmed, finished;
    }
    private static final class Board {
        final JSONObject data; final long received;
        Board(JSONObject data, long received) { this.data = data; this.received = received; }
    }
    private static final class Arrival {
        final String train; final long at;
        Arrival(String train, long at) { this.train = train; this.at = at; }
    }
    private static final class Option {
        final String train; final long departure, arrival;
        Option(String train, long departure, long arrival) { this.train = train; this.departure = departure; this.arrival = arrival; }
    }
    final ServicePlan plan;
    final long anchor, startedAt;
    final Set<String> sent = new HashSet<>();
    private final Map<String, Board> boards = new HashMap<>();
    private final Map<Integer, TakeWatch> takes = new HashMap<>();
    private JSONObject map;
    private long mapReceived;
    private boolean takeStateDirty;
    private static final String[] NAMES = {"La Pau","Besòs","Besòs Mar","El Maresme | Fòrum","Selva de Mar","Poblenou","Llacuna","Bogatell","Ciutadella | Vila Olímpica","Barceloneta","Jaume I","Urquinaona","Passeig de Gràcia","Girona","Verdaguer","Joanic","Alfons X","Guinardó | Hospital de Sant Pau","Maragall","Llucmajor","Via Júlia","Trinitat Nova"};
    private static final Map<String, Integer> STOP_POSITIONS = new HashMap<>();
    static {
        for (int i = 0; i < NAMES.length; i++) STOP_POSITIONS.put(stationKey(NAMES[i]), 413 + i);
        STOP_POSITIONS.put("maresme", 416);
        STOP_POSITIONS.put("elmaresme", 416);
        STOP_POSITIONS.put("maresmeforum", 416);
        STOP_POSITIONS.put("forum", 416);
        STOP_POSITIONS.put("ciutadella", 421);
        STOP_POSITIONS.put("vilaolimpica", 421);
        STOP_POSITIONS.put("pggracia", 425);
        STOP_POSITIONS.put("guinardo", 430);
        STOP_POSITIONS.put("hospitaldesantpau", 430);
    }

    ServiceAlerts(ServicePlan plan, long now) { this.plan = plan; anchor = plan.anchorAt(now); startedAt = now; }

    Set<String> requiredStops(long now, int margin) {
        Set<String> stops = new LinkedHashSet<>();
        for (ServicePlan.Row row : plan.rows) {
            if (!row.driving()) continue;
            long at = plan.at(anchor, row.start);
            String source = plan.sourceFor(row);
            if (isDirectTake(row, source)) {
                if (!pendingTake(row, at, now)) continue;
                stops.add(row.from);
            } else {
                if (at + LIVE_MATCH_WINDOW <= now) continue;
                stops.add(source);
                stops.add(row.from);
            }
            if (at >= now) break;
        }
        for (ServicePlan.Row row : plan.rows) {
            if (releasedTrainFor(row) == null || sent.contains(RETURN_ALERT_PREFIX + row.index)) continue;
            long at = plan.at(anchor, row.start);
            if (now >= at - LIVE_MATCH_WINDOW - margin * 60000L && now <= at + LIVE_MATCH_WINDOW) {
                stops.add(row.from);
                stops.add(row.to);
            }
        }
        return stops;
    }

    void acceptBoard(JSONObject raw, long now) {
        JSONObject data = raw.optJSONObject("data"); if (data == null) data = raw;
        for (String field : new String[]{"stopCode","stop","stopId","stationId","stationCode","id"}) {
            String stop = data.optString(field, "");
            JSONObject object = data.optJSONObject(field);
            if (object != null) stop = object.optString("code", object.optString("id", ""));
            if (!stop.matches("41[3-9]|42[0-9]|43[0-4]")) stop = raw.optString(field, "");
            if (stop.matches("41[3-9]|42[0-9]|43[0-4]")) {
                boards.put(stop, new Board(data, now));
                return;
            }
        }
    }

    long predictedArrival(String stop, String via, String train, long now) {
        if (stop == null || via == null || train == null || !("1".equals(via) || "2".equals(via))) return 0;
        for (Arrival arrival : arrivals(stop, via, now)) {
            if (train.equals(arrival.train) && arrival.at > now) return arrival.at;
        }
        return 0;
    }

    void acceptMap(JSONObject trains, long now) {
        map = trains;
        mapReceived = now;
    }

    void clearLiveData() {
        boards.clear();
        map = null;
    }

    boolean hasPending(long now) {
        for (ServicePlan.Row row : plan.rows) {
            long at = plan.at(anchor, row.start);
            if (row.driving() && isDirectTake(row, plan.sourceFor(row))) {
                if (pendingTake(row, at, now)) return true;
                continue;
            }
            if (at + LIVE_MATCH_WINDOW <= now) continue;
            if ((row.driving() || "TRA".equals(row.type)) && at >= startedAt - 30000) return true;
            if (releasedTrainFor(row) != null && !sent.contains(RETURN_ALERT_PREFIX + row.index)) return true;
        }
        return false;
    }

    List<Alert> due(long now, int margin) {
        List<Alert> alerts = new ArrayList<>();
        for (ServicePlan.Row row : plan.rows) {
            long at = plan.at(anchor, row.start);
            String key = row.type + "-" + row.index;
            String source = plan.sourceFor(row);
            if (row.driving() && isDirectTake(row, source)) {
                Alert take = liveTakeAlert(row, key, at, now, margin);
                if (take != null) alerts.add(take);
                continue;
            }
            if (now > at + LIVE_MATCH_WINDOW) continue;
            boolean scheduledAlert = at >= startedAt - 30000;
            if ("TRA".equals(row.type)) {
                Alert returning = returnAlert(row, at, now, margin);
                if (returning != null) {
                    alerts.add(returning);
                } else if (scheduledAlert && now >= at - margin * 60000L && now < at + 60000 && !sent.contains(key)) {
                    alerts.add(new Alert(key, "Servei · trasllat", "Trasllat " + station(row.from) + " → " + station(row.to) + " a les " + clock(at) + "."));
                }
            } else if (row.driving() && scheduledAlert) {
                Alert connection = connectionAlert(row, now, margin);
                if (connection != null) {
                    alerts.add(connection);
                    continue;
                }
                String via = displacementVia(source, row.from);
                Option own = usesConnectionOptions(row, via) ? null : ownTrain(row, source, now);
                if (approaching(own, now, margin) && !sent.contains("pas-" + row.index)) {
                    alerts.add(new Alert("pas-" + row.index, "Puja al tren " + row.train,
                        "El " + row.train + " passa per " + station(source) + " · via " + via + " a les " + clock(own.departure) + ". Puja-hi per arribar a la presa a " + station(row.from) + ". " + advice(row, now), own.departure));
                } else if (!sent.contains(key)) {
                    Alert take = scheduledTakeAlert(row, key, at, now, margin);
                    if (take != null) alerts.add(take);
                }
            }
        }
        return alerts;
    }

    void markSent(Alert alert) {
        if (alert.takeRow >= 0) {
            TakeWatch watch = takes.get(alert.takeRow);
            watch.announcedArrival = alert.expiresAt;
            watch.rearmed = false;
            takeStateDirty = true;
            return;
        }
        sent.add(alert.key);
        if (alert.key.startsWith("pas-")) sent.add("CON-" + alert.key.substring(4));
        if (alert.key.startsWith(RETURN_ALERT_PREFIX)) sent.add("TRA-" + alert.key.substring(RETURN_ALERT_PREFIX.length()));
    }

    private static boolean isDirectTake(ServicePlan.Row row, String source) {
        return source.isEmpty() || source.equals(row.from);
    }

    private boolean pendingTake(ServicePlan.Row row, long scheduled, long now) {
        if (!row.from.matches("41[3-9]|42[0-9]|43[0-4]") || !("1".equals(row.via) || "2".equals(row.via))) return false;
        TakeWatch watch = takes.get(row.index);
        return watch == null ? now <= scheduled + TAKE_MATCH_WINDOW : !watch.finished && now - watch.seenAt < TAKE_MATCH_WINDOW;
    }

    private Alert liveTakeAlert(ServicePlan.Row row, String key, long scheduled, long now, int margin) {
        if (!pendingTake(row, scheduled, now) || map == null || now < mapReceived || now - mapReceived >= 45000) return null;
        JSONObject train = map.optJSONObject(row.train);
        if (train == null) return null;
        int position = stopPosition(train.optString("estacion", ""));
        if (position == 0) return null;
        int target = Integer.parseInt(row.from);
        boolean terminus = target == 413 || target == 434;
        String inbound = target == 413 ? "2" : target == 434 ? "1" : row.via;
        String track = train.optString("platformTrack", train.optString("track", ""));
        boolean onApproach = inbound.equals(track) || (terminus && position == target);
        int distance = (target - position) * ("1".equals(inbound) ? 1 : -1);
        TakeWatch watch = takes.get(row.index);
        // Once the train has gone by, the next prediction for its number is another circuit.
        if (watch != null && (distance < 0 || (onApproach && distance > watch.distance + 1)
            || (!onApproach && (terminus || watch.distance == 0) && ("1".equals(track) || "2".equals(track))))) {
            watch.finished = true;
            takeStateDirty = true;
            return null;
        }
        if (!onApproach || distance < 0) return null;

        // The timetable identifies the initial passage. Later changes use that passage's last prediction.
        long reference = watch == null ? scheduled : watch.forecast;
        long arrival = 0;
        for (Arrival candidate : arrivals(row.from, row.via, now)) {
            if (!row.train.equals(candidate.train) || candidate.at <= now) continue;
            if (watch == null && Math.abs(candidate.at - scheduled) > TAKE_MATCH_WINDOW) continue;
            if (arrival == 0 || Math.abs(candidate.at - reference) < Math.abs(arrival - reference)) arrival = candidate.at;
        }
        if (arrival == 0) return null;

        if (watch == null) {
            watch = new TakeWatch();
            watch.distance = distance;
            takes.put(row.index, watch);
            takeStateDirty = true;
        }
        if (distance < watch.distance || watch.seenAt / 60000 != now / 60000) takeStateDirty = true;
        watch.distance = Math.min(watch.distance, distance);
        watch.forecast = arrival;
        watch.seenAt = now;

        long change = arrival - watch.announcedArrival;
        if (arrival > now + margin * 60000L) {
            if (watch.announcedArrival != 0 && change >= NOTICE_CHANGE && !watch.rearmed) {
                watch.rearmed = true;
                takeStateDirty = true;
            }
            return null;
        }
        boolean first = watch.announcedArrival == 0;
        if (!first && change > -NOTICE_CHANGE && !(watch.rearmed && change >= NOTICE_CHANGE)) {
            if (watch.rearmed) {
                watch.rearmed = false;
                takeStateDirty = true;
            }
            return null;
        }
        String body = station(row.from) + " · via " + row.via + " · arribada estimada " + clock(arrival)
            + " segons Miralin. Presa prevista al servei a les " + clock(scheduled) + ".";
        return new Alert(key, (first ? "Servei" : "Nova previsió") + " · presa del " + row.train, body, arrival, row.index);
    }

    void restoreTakeState(String value) {
        try {
            JSONArray state = new JSONArray(value);
            for (int i = 0; i < state.length(); i++) {
                JSONObject saved = state.getJSONObject(i);
                TakeWatch watch = new TakeWatch();
                watch.distance = saved.getInt("distance");
                watch.forecast = saved.getLong("forecast");
                watch.seenAt = saved.getLong("seenAt");
                watch.announcedArrival = saved.optLong("announcedArrival");
                watch.rearmed = saved.optBoolean("rearmed");
                watch.finished = saved.optBoolean("finished");
                takes.put(saved.getInt("row"), watch);
            }
        } catch (JSONException ignored) {}
    }

    String changedTakeState() {
        if (!takeStateDirty) return null;
        JSONArray state = new JSONArray();
        for (Map.Entry<Integer, TakeWatch> entry : takes.entrySet()) {
            TakeWatch watch = entry.getValue();
            try {
                state.put(new JSONObject().put("row", entry.getKey()).put("distance", watch.distance)
                    .put("forecast", watch.forecast).put("seenAt", watch.seenAt).put("announcedArrival", watch.announcedArrival)
                    .put("rearmed", watch.rearmed).put("finished", watch.finished));
            } catch (JSONException ignored) {}
        }
        takeStateDirty = false;
        return state.toString();
    }

    private Alert scheduledTakeAlert(ServicePlan.Row row, String key, long scheduled, long now, int margin) {
        long lead = margin * 60000L;
        if (now < scheduled - lead || now >= scheduled + 60000) return null;
        return new Alert(key, "Servei · presa del " + row.train,
            "Presa prevista a " + station(row.from) + " · via " + row.via + " · " + clock(scheduled) + ". " + advice(row, now));
    }

    private ServicePlan.Row releasedTrainFor(ServicePlan.Row transfer) {
        if (!"TRA".equals(transfer.type) || transfer.index == 0 || transfer.from.equals(transfer.to)) return null;
        ServicePlan.Row previous = plan.rows.get(transfer.index - 1);
        String via = displacementVia(transfer.from, transfer.to);
        if (!previous.driving() || previous.end != transfer.start || !previous.to.equals(transfer.from)) return null;
        if (!("1".equals(previous.out) || "2".equals(previous.out)) || via.isEmpty() || via.equals(previous.out)) return null;
        return previous;
    }

    private Alert returnAlert(ServicePlan.Row transfer, long scheduled, long now, int margin) {
        String key = RETURN_ALERT_PREFIX + transfer.index;
        if (sent.contains(key) || now < scheduled - LIVE_MATCH_WINDOW - margin * 60000L) return null;
        ServicePlan.Row released = releasedTrainFor(transfer);
        if (released == null || now < plan.at(anchor, released.start)) return null;

        // Select the predicted release on the outgoing track before matching the train's next pass on the opposite track.
        long handover = arrivalAtOrAfter(arrivals(transfer.from, released.out, now), released.train,
            Math.max(now + 1, scheduled - LIVE_MATCH_WINDOW));
        if (handover == 0 || handover > scheduled + LIVE_MATCH_WINDOW || handover > now + margin * 60000L) return null;

        String via = displacementVia(transfer.from, transfer.to);
        List<Arrival> departures = arrivals(transfer.from, via, now);
        List<Arrival> destination = arrivals(transfer.to, via, now);
        long returnPass = arrivalAtOrAfter(departures, released.train, handover + 1);
        if (returnPass == 0) return null;
        long returnArrival = arrivalAtOrAfter(destination, released.train, returnPass + 1);
        if (returnArrival == 0) return null;

        Option earlier = null;
        boolean complete = true;
        for (Arrival departure : departures) {
            if (departure.at <= handover) continue;
            if (departure.at >= returnPass) break;
            long arrival = arrivalAtOrAfter(destination, departure.train, departure.at + 1);
            if (arrival == 0) complete = false;
            if (arrival > 0 && arrival < returnArrival && (earlier == null || arrival < earlier.arrival)) {
                earlier = new Option(departure.train, departure.at, arrival);
            }
        }

        String origin = station(transfer.from), target = station(transfer.to);
        if (earlier != null) {
            String body = origin + " · via " + via + " · pas previst " + clock(earlier.departure)
                + " · marge estimat de canvi " + marginText(earlier.departure - handover) + ". Arribada a " + target
                + " cap a les " + clock(earlier.arrival) + " · " + marginText(returnArrival - earlier.arrival)
                + " abans del " + released.train + " (" + clock(returnArrival) + ").";
            return new Alert(key, "El " + earlier.train + " passa abans del " + released.train, body, handover);
        }
        if (!complete) return null;
        String body = "El " + released.train + " torna per " + origin + " · via " + via + " · " + clock(returnPass)
            + " i arriba a " + target + " cap a les " + clock(returnArrival) + ".";
        return new Alert(key, "El " + released.train + " és la primera arribada prevista", body, handover);
    }

    private Alert connectionAlert(ServicePlan.Row row, long now, int margin) {
        ServicePlan.Row transfer = plan.transferFor(row);
        if (transfer == null || transfer.from.equals(row.from)) return null;
        String source = transfer.from, via = displacementVia(source, row.from);
        if (!usesConnectionOptions(row, via)) return null;
        long transferStart = plan.at(anchor, transfer.start);
        if (now < transferStart - margin * 60000L) return null;

        long deadline = assignedArrival(row, now);
        if (deadline <= now) return null;
        List<Option> options = options(row, source, deadline, now);
        options.removeIf(option -> option.departure < transferStart);
        if (options.isEmpty()) return null;

        options.sort(Comparator.comparingLong((Option option) -> option.departure).reversed());
        if (options.size() > 1) {
            List<Option> candidates = options.subList(0, Math.min(3, options.size()));
            Option first = candidates.get(candidates.size() - 1);
            String key = "enllacos-" + row.index;
            if (!approaching(first, now, margin) || sent.contains(key)) return null;
            String body = station(source) + " · via " + via + " → " + station(row.from)
                + " · presa per via " + row.via + ". " + formatOptions(candidates, deadline);
            return new Alert(key, "Enllaços per prendre el " + row.train, body, first.departure);
        }

        Option last = options.get(0);
        if (!approaching(last, now, margin)) return null;
        String key = "enllac-" + row.index + "-" + last.train;
        if (sent.contains(key)) return null;

        String body = "Últim enllaç segons Miralin. " + station(source) + " · via " + via + " · pas previst " + clock(last.departure)
            + ". Arribada a " + station(row.from) + " cap a les " + clock(last.arrival) + " · marge estimat "
            + marginText(deadline - last.arrival) + " per prendre el " + row.train + " per via " + row.via + ".";
        return new Alert(key, "Puja al tren " + last.train, body, last.departure);
    }

    private static boolean usesConnectionOptions(ServicePlan.Row row, String travelVia) {
        return !travelVia.isEmpty() && !travelVia.equals(row.via)
            && !"413".equals(row.from) && !"434".equals(row.from);
    }

    private static boolean approaching(Option option, long now, int margin) {
        return option != null && option.departure > now + 5000 && option.departure <= now + margin * 60000L;
    }

    String advice(ServicePlan.Row row, long now) {
        String source = plan.sourceFor(row);
        if (source.isEmpty() || source.equals(row.from)) return "";
        if (fresh(source, now) == null || fresh(row.from, now) == null) return "Sense arribades recents de Miralin per calcular alternatives.";
        long deadline = assignedArrival(row, now);
        if (deadline == 0) return "Miralin no mostra una arribada recent del " + row.train + " per via " + row.via + "; no es pot confirmar el marge.";
        String via = displacementVia(source, row.from);
        if (!usesConnectionOptions(row, via)) {
            Option own = ownTrain(row, source, now);
            if (own != null) return "Darrera opció: pots desplaçar-te amb el mateix " + row.train + " des de " + station(source) + " · via " + via + " · " + clock(own.departure) + ".";
            return "Miralin encara no confirma el desplaçament amb el " + row.train + " des de " + station(source) + ".";
        }
        List<Option> options = options(row, source, deadline, now);
        if (options.isEmpty()) return "Miralin no mostra cap tren que hi arribi a temps.";
        return "Alternatives des de " + station(source) + " · via " + via + ": " + formatOptions(options, deadline);
    }

    private String formatOptions(List<Option> options, long deadline) {
        options.sort(Comparator.comparingLong((Option option) -> option.arrival).reversed());
        StringBuilder result = new StringBuilder();
        int count = Math.min(3, options.size());
        for (int i = 0; i < count; i++) {
            Option option = options.get(i);
            if (i > 0) result.append("; ");
            result.append(option.train).append(" (pas ").append(clock(option.departure))
                .append(", arribada ").append(clock(option.arrival)).append(", marge ")
                .append(marginText(deadline - option.arrival)).append(')');
        }
        return result.append('.').toString();
    }

    private static String marginText(long millis) {
        return millis >= 60000 ? (millis / 60000) + " min" : (millis / 1000) + " s";
    }

    private Option ownTrain(ServicePlan.Row row, String source, long now) {
        if (source.isEmpty()) return null;
        long deadline = assignedArrival(row, now); if (deadline == 0) return null;
        for (Arrival departure : arrivals(source, displacementVia(source, row.from), now))
            if (departure.train.equals(row.train) && departure.at > now + 5000 && deadline > departure.at)
                return new Option(row.train, departure.at, deadline);
        return null;
    }

    private long assignedArrival(ServicePlan.Row row, long now) {
        return arrivalAtOrAfter(arrivals(row.from, row.via, now), row.train, 0);
    }

    private static long arrivalAtOrAfter(List<Arrival> arrivals, String train, long earliest) {
        for (Arrival arrival : arrivals) {
            if (arrival.at >= earliest && arrival.train.equals(train)) return arrival.at;
        }
        return 0;
    }

    private List<Option> options(ServicePlan.Row row, String source, long deadline, long now) {
        String via = displacementVia(source, row.from);
        List<Arrival> destination = arrivals(row.from, via, now);
        List<Option> options = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Arrival departure : arrivals(source, via, now)) {
            if (departure.at <= now + 5000 || !seen.add(departure.train)) continue;
            long arrival = arrivalAtOrAfter(destination, departure.train, departure.at);
            if (arrival > 0 && arrival < deadline) options.add(new Option(departure.train, departure.at, arrival));
        }
        return options;
    }

    private Board fresh(String stop, long now) {
        Board board = boards.get(stop); return board != null && now - board.received < 60000 && now >= board.received ? board : null;
    }

    private List<Arrival> arrivals(String stop, String via, long now) {
        List<Arrival> result = new ArrayList<>(); Board board = fresh(stop, now); if (board == null) return result;
        JSONArray entries = board.data.optJSONArray("via" + via);
        boolean needsVia = entries == null;
        if (entries == null) entries = board.data.optJSONArray("lineTrains");
        if (entries == null) {
            return result;
        }
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null) continue;
            String track = entry.optString("platformTrack", entry.optString("track", entry.optString("via", "")));
            if (needsVia && !via.equals(track)) continue;
            String train = entry.optString("trainCode", entry.optString("tren", entry.optString("id", "")));
            long at = arrivalTime(entry, board.received);
            if (!train.isEmpty() && at >= now - 15000) result.add(new Arrival(train, at));
        }
        result.sort(Comparator.comparingLong(entry -> entry.at)); return result;
    }

    private static long arrivalTime(JSONObject entry, long received) {
        Object value = entry.opt("arrivalTime");
        if (value instanceof Number && ((Number) value).longValue() > 100000000000L) return ((Number) value).longValue();
        if (value instanceof String) try { return Instant.parse((String) value).toEpochMilli(); } catch (Exception ignored) {}
        double seconds = entry.optDouble("secsLeft", Double.NaN);
        return Double.isFinite(seconds) ? received + Math.round(seconds * 1000) : 0;
    }

    static String displacementVia(String source, String target) {
        try { return Integer.parseInt(target) > Integer.parseInt(source) ? "1" : "2"; } catch (NumberFormatException ignored) { return ""; }
    }
    static String station(String stop) {
        try { int index = Integer.parseInt(stop) - 413; return index >= 0 && index < NAMES.length ? NAMES[index] : stop; } catch (NumberFormatException ignored) { return stop; }
    }
    private static int stopPosition(String station) {
        if (station.matches("41[3-9]|42[0-9]|43[0-4]")) return Integer.parseInt(station);
        return STOP_POSITIONS.getOrDefault(stationKey(station), 0);
    }
    private static String stationKey(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private static String clock(long time) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ServicePlan.ZONE.toZoneId()).format(Instant.ofEpochMilli(time)); }
}
