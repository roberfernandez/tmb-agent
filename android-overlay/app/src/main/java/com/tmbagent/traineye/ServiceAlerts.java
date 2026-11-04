package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local, per-device advice. Never treats old station boards as live arrivals. */
final class ServiceAlerts {
    static final class Alert {
        final String key, title, body;
        Alert(String key, String title, String body) { this.key = key; this.title = title; this.body = body; }
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
    private static final String[] NAMES = {"La Pau","Besòs","Besòs Mar","El Maresme | Fòrum","Selva de Mar","Poblenou","Llacuna","Bogatell","Ciutadella | Vila Olímpica","Barceloneta","Jaume I","Urquinaona","Passeig de Gràcia","Girona","Verdaguer","Joanic","Alfons X","Guinardó | Hospital de Sant Pau","Maragall","Llucmajor","Via Júlia","Trinitat Nova"};

    ServiceAlerts(ServicePlan plan, long now) { this.plan = plan; anchor = plan.anchorAt(now); startedAt = now; }

    Set<String> requiredStops(long now) {
        Set<String> stops = new LinkedHashSet<>();
        ServicePlan.Row row = plan.nextDriving(now, anchor);
        if (row != null) {
            String source = plan.sourceFor(row);
            if (!source.isEmpty()) stops.add(source);
            if (!row.from.isEmpty()) stops.add(row.from);
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
                boards.put(stop, new Board(data, now)); return;
            }
        }
    }

    void clearBoards() { boards.clear(); }

    boolean hasPending(long now) {
        for (ServicePlan.Row row : plan.rows) {
            long at = plan.at(anchor, row.start);
            if ((row.driving() || "TRA".equals(row.type)) && at >= startedAt - 30000 && at + 15 * 60000L > now) return true;
        }
        return false;
    }

    List<Alert> due(long now, int margin) {
        List<Alert> alerts = new ArrayList<>();
        for (ServicePlan.Row row : plan.rows) {
            long at = plan.at(anchor, row.start);
            if (at < startedAt - 30000 || now > at + 15 * 60000L) continue;
            String key = row.type + "-" + row.index;
            if ("TRA".equals(row.type) && now >= at - margin * 60000L && now < at + 60000 && !sent.contains(key)) {
                alerts.add(new Alert(key, "Servei · trasllat", "Trasllat " + station(row.from) + " → " + station(row.to) + " a les " + clock(at) + "."));
            } else if (row.driving()) {
                String source = plan.sourceFor(row);
                Option own = ownTrain(row, source, now);
                if (own != null && own.departure > now + 5000 && own.departure <= now + margin * 60000L && !sent.contains("pas-" + row.index)) {
                    alerts.add(new Alert("pas-" + row.index, "Puja al tren " + row.train,
                        "El " + row.train + " passa per " + station(source) + " · via " + displacementVia(source, row.from) + " a les " + clock(own.departure) + ". Puja-hi per arribar a la presa a " + station(row.from) + ". " + advice(row, now)));
                } else if (now >= at - margin * 60000L && now < at + 60000 && !sent.contains(key)) {
                    alerts.add(new Alert(key, "Servei · presa del " + row.train,
                        "Presa prevista a " + station(row.from) + " · via " + row.via + " · " + clock(at) + ". " + advice(row, now)));
                }
            }
        }
        return alerts;
    }

    void markSent(Alert alert) {
        sent.add(alert.key);
        if (alert.key.startsWith("pas-")) sent.add("CON-" + alert.key.substring(4));
    }

    String advice(ServicePlan.Row row, long now) {
        String source = plan.sourceFor(row);
        if (source.isEmpty() || source.equals(row.from)) return "";
        if (fresh(source, now) == null || fresh(row.from, now) == null) return "Sense arribades recents de Miralin per calcular alternatives.";
        long deadline = assignedArrival(row, now);
        if (deadline == 0) return "Miralin no mostra una arribada recent del " + row.train + " per via " + row.via + "; no es pot confirmar el marge.";
        Option own = ownTrain(row, source, now);
        if (own != null) return "Darrera opció: pots desplaçar-te amb el mateix " + row.train + " des de " + station(source) + " · via " + displacementVia(source, row.from) + " · " + clock(own.departure) + ".";
        if (row.via.equals(displacementVia(source, row.from))) return "El tren assignat encara no apareix entre les arribades de l’origen per la mateixa via.";
        List<Option> options = options(row, source, deadline, now);
        if (options.isEmpty()) return "Miralin no mostra cap tren que hi arribi a temps.";
        options.sort((a,b) -> Long.compare(b.arrival, a.arrival));
        StringBuilder result = new StringBuilder("Alternatives des de " + station(source) + " · via " + displacementVia(source, row.from) + ": ");
        int count = Math.min(3, options.size());
        for (int i = 0; i < count; i++) {
            Option option = options.get(i);
            if (i > 0) result.append("; ");
            result.append(option.train).append(" (").append(clock(option.arrival)).append(", marge ")
                .append(Math.max(0, (deadline - option.arrival) / 60000)).append(" min)");
        }
        return result.append('.').toString();
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
        for (Arrival entry : arrivals(row.from, row.via, now)) if (entry.train.equals(row.train)) return entry.at;
        return 0;
    }

    private List<Option> options(ServicePlan.Row row, String source, long deadline, long now) {
        String via = displacementVia(source, row.from);
        List<Arrival> destination = arrivals(row.from, via, now);
        List<Option> options = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Arrival departure : arrivals(source, via, now)) {
            if (departure.at < now + 5000 || !seen.add(departure.train)) continue;
            for (Arrival arrival : destination) {
                if (arrival.train.equals(departure.train) && arrival.at >= departure.at && arrival.at <= deadline) {
                    options.add(new Option(departure.train, departure.at, arrival.at)); break;
                }
            }
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
        if (entries == null) return result;
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
    private static String clock(long time) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ServicePlan.ZONE.toZoneId()).format(Instant.ofEpochMilli(time)); }
}
