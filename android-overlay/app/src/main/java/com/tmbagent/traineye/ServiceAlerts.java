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
    static final long BOARD_FRESH_MS = 60_000L;
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
        long seenAt, announcedArrival;
        boolean rearmed, finished;
    }
    private static final class Board {
        final JSONObject raw, data;
        final String declaredStop;
        final long received;
        Board(JSONObject raw, long received) {
            this.raw = raw;
            JSONObject nested = raw.optJSONObject("data");
            data = nested == null ? raw : nested;
            declaredStop = boardStop(raw);
            this.received = received;
        }
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
    final PickupEta pickupEta;
    private JSONObject map;
    private long mapReceived;
    private boolean takeStateDirty;
    static final int FIRST_STOP = 413;
    private static final String[] NAMES = {"La Pau","Besòs","Besòs Mar","El Maresme | Fòrum","Selva de Mar","Poblenou","Llacuna","Bogatell","Ciutadella | Vila Olímpica","Barceloneta","Jaume I","Urquinaona","Passeig de Gràcia","Girona","Verdaguer","Joanic","Alfons X","Guinardó | Hospital de Sant Pau","Maragall","Llucmajor","Via Júlia","Trinitat Nova"};
    private static final Map<String, Integer> STOP_POSITIONS = new HashMap<>();
    static {
        for (int i = 0; i < NAMES.length; i++) STOP_POSITIONS.put(stationKey(NAMES[i]), FIRST_STOP + i);
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

    ServiceAlerts(ServicePlan plan, long now) {
        this(plan, now, new PickupEta(plan, now, java.util.Collections.emptySet()));
    }

    ServiceAlerts(ServicePlan plan, long now, PickupEta pickupEta) {
        this.plan = plan;
        this.pickupEta = pickupEta;
        anchor = plan.anchorAt(now);
        startedAt = now;
    }

    Set<String> requiredStops(long now, int margin) {
        Set<String> stops = new LinkedHashSet<>();
        for (ServicePlan.Row row : plan.rows) {
            if (!pickupEta.isPickup(row) || pickupEta.isComplete(row)) continue;
            long at = plan.at(anchor, row.start);
            String source = plan.sourceFor(row);
            if (isDirectTake(row, source)) {
                if (!pendingTake(row, at, now)) continue;
                stops.add(row.from);
            } else {
                if (!pendingConnection(row, at, now)) continue;
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

    static String boardStop(JSONObject raw) {
        JSONObject data = raw.optJSONObject("data");
        for (String field : new String[]{"stopCode", "stop", "stopId", "stationId", "stationCode", "id"}) {
            String stop = data == null ? "" : stopField(data, field);
            if (!stop.matches("41[3-9]|42[0-9]|43[0-4]")) stop = stopField(raw, field);
            if (stop.matches("41[3-9]|42[0-9]|43[0-4]")) return stop;
        }
        return "";
    }

    private static String stopField(JSONObject board, String field) {
        JSONObject object = board.optJSONObject(field);
        return object == null ? board.optString(field, "") : object.optString("code", object.optString("id", ""));
    }

    void acceptBoard(JSONObject raw, long now) {
        String stop = boardStop(raw);
        if (!stop.isEmpty()) acceptBoard(stop, raw, now);
    }

    void acceptBoard(String stop, JSONObject raw, long now) {
        boards.put(stop, new Board(raw, now));
    }

    JSONObject diagnosticBoard(String stop) throws JSONException {
        Board board = boards.get(stop);
        return new JSONObject().put("requestedStop", stop)
            .put("declaredStop", board == null ? "" : board.declaredStop)
            .put("receivedAt", board == null ? 0 : board.received)
            .put("raw", board == null ? JSONObject.NULL : board.raw);
    }

    private static String currentTrainStop(JSONObject entry) {
        // A train's id can also be 413..434. It is never a destination label.
        for (String field : new String[]{"stopCode", "stop", "stopId", "stationId", "stationCode"}) {
            String stop = stopField(entry, field);
            if (stop.matches("41[3-9]|42[0-9]|43[0-4]")) return stop;
        }
        return "";
    }

    private static String arrivalScopeError(Board board, String stop, boolean lineTrains) {
        // The captured stopDataUpdate has two different clocks: via1/via2 contain
        // forecasts for the parent board's stop, while lineTrains contains current
        // position timers. A row's stopCode/stopName identifies the train's current
        // location, not the destination of its station-board forecast.
        if (lineTrains) return "lineTrains contains position timers";
        if (board.declaredStop.isEmpty()) return "board has no destination stop";
        return stop.equals(board.declaredStop) ? "" : "board belongs to " + board.declaredStop;
    }

    long boardReceivedAt(String stop) {
        Board board = boards.get(stop);
        return board == null ? 0 : board.received;
    }

    String boardStatus(String stop, String train, long now) {
        train = TrainId.normalize(train);
        Board board = fresh(stop, now);
        if (board == null) return "no fresh board for stop " + stop;
        StringBuilder summary = new StringBuilder("stop=").append(stop)
            .append(" declared=").append(board.declaredStop.isEmpty() ? "missing" : board.declaredStop);
        for (String source : new String[]{"via1", "via2", "lineTrains"}) {
            JSONArray entries = board.data.optJSONArray(source);
            summary.append(" ").append(source).append("=");
            if (entries == null) { summary.append("missing"); continue; }
            summary.append(entries.length());
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                String code = TrainId.fromBoard(entry);
                if (train.equals(code)) {
                    String scopeError = arrivalScopeError(board, stop, "lineTrains".equals(source));
                    summary.append(" MATCH train=").append(code)
                        .append(" currentStop=").append(currentTrainStop(entry))
                        .append(" arrival=").append(arrivalTime(entry, board.received))
                        .append(" secsLeft=").append(entry.opt("secsLeft"))
                        .append(" scope=").append(scopeError.isEmpty() ? "accepted" : "rejected: " + scopeError);
                }
            }
        }
        return summary.toString();
    }

    long predictedArrival(String stop, String via, String train, long now) {
        return predictedArrival(stop, via, train, now, BOARD_FRESH_MS);
    }

    long predictedArrival(String stop, String via, String train, long now, long maxBoardAge) {
        train = TrainId.normalize(train);
        if (stop == null || via == null || train == null || !("1".equals(via) || "2".equals(via))) return 0;
        for (Arrival arrival : arrivals(stop, via, now, maxBoardAge)) {
            if (train.equals(arrival.train) && arrival.at > now) return arrival.at;
        }
        return 0;
    }

    long predictedArrivalEitherTrack(String stop, String preferredVia, String train, long now) {
        return predictedArrivalEitherTrack(stop, preferredVia, train, now, BOARD_FRESH_MS);
    }

    long predictedArrivalEitherTrack(String stop, String preferredVia, String train, long now, long maxBoardAge) {
        // La Pau (413) and Trinitat Nova (434) have one operational platform.
        // Miralin's via label is not a constraint at these terminal stations.
        if (("413".equals(stop) || "434".equals(stop))) preferredVia = "";
        if ("1".equals(preferredVia) || "2".equals(preferredVia)) {
            long preferred = predictedArrival(stop, preferredVia, train, now, maxBoardAge);
            if (preferred > now) return preferred;
            return predictedArrival(stop, "1".equals(preferredVia) ? "2" : "1", train, now, maxBoardAge);
        }
        long first = predictedArrival(stop, "1", train, now, maxBoardAge);
        long second = predictedArrival(stop, "2", train, now, maxBoardAge);
        if (first <= now) return second;
        if (second <= now) return first;
        return Math.min(first, second);
    }

    void acceptMap(JSONObject trains, long now) {
        map = TrainId.positions(trains);
        mapReceived = now;
    }

    void clearLiveData() {
        boards.clear();
        map = null;
    }

    boolean hasPending(long now) {
        for (ServicePlan.Row row : plan.rows) {
            long at = plan.at(anchor, row.start);
            if (row.driving()) {
                if (pickupEta.isPickup(row) && !pickupEta.isComplete(row)
                    && (isDirectTake(row, plan.sourceFor(row)) ? pendingTake(row, at, now)
                        : pendingConnection(row, at, now))) return true;
                continue;
            }
            if (at + LIVE_MATCH_WINDOW <= now) continue;
            if ("TRA".equals(row.type) && at >= startedAt - 30000) return true;
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
            if (row.driving()) {
                if (!pickupEta.isPickup(row) || pickupEta.isComplete(row)) continue;
                if (isDirectTake(row, source)) {
                    Alert take = liveTakeAlert(row, key, at, now, margin);
                    if (take != null) alerts.add(take);
                    continue;
                }
                if (!pendingConnection(row, at, now)) continue;
            } else if (now > at + LIVE_MATCH_WINDOW) continue;
            boolean scheduledAlert = at >= startedAt - 30000;
            if ("TRA".equals(row.type)) {
                Alert returning = returnAlert(row, at, now, margin);
                if (returning != null) {
                    alerts.add(returning);
                } else if (scheduledAlert && now >= at - margin * 60000L && now < at + 60000 && !sent.contains(key)) {
                    alerts.add(new Alert(key, "Servei · trasllat", "Trasllat " + station(row.from) + " → " + station(row.to) + " a les " + clock(at) + "."));
                }
            } else if (row.driving()) {
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

    private boolean pendingConnection(ServicePlan.Row row, long scheduled, long now) {
        return now <= scheduled + LIVE_MATCH_WINDOW
            || (pickupEta.isTracking(row) && now < plan.at(anchor, row.end));
    }

    private boolean pendingTake(ServicePlan.Row row, long scheduled, long now) {
        if (pickupEta.isComplete(row)) return false;
        TakeWatch watch = takes.get(row.index);
        if (watch != null && watch.finished) return false;
        return now <= scheduled + TAKE_MATCH_WINDOW
            || (pickupEta.isTracking(row) && now < plan.at(anchor, row.end))
            || (watch != null && now - watch.seenAt < TAKE_MATCH_WINDOW);
    }

    private JSONObject livePosition(ServicePlan.Row row, long now) {
        if (map == null || now < mapReceived || now - mapReceived >= 45_000L) return null;
        return map.optJSONObject(row.train);
    }

    private Alert liveTakeAlert(ServicePlan.Row row, String key, long scheduled, long now, int margin) {
        if (!pendingTake(row, scheduled, now)) return null;
        ServicePlan.Row active = pickupEta.target(now, row.train);
        if (active == null || active.index != row.index) return null;
        JSONObject train = livePosition(row, now);
        if (train == null) return null;
        TakeWatch watch = takes.get(row.index);
        if (pickupEta.observe(row, train, mapReceived)) {
            if (watch == null) {
                watch = new TakeWatch();
                takes.put(row.index, watch);
            }
            watch.finished = true;
            takeStateDirty = true;
            return null;
        }

        int position = stopPosition(train.optString("estacion", ""));
        if (position == 0) return null;
        String inbound = "413".equals(row.from) ? "2" : "434".equals(row.from) ? "1" : row.via;
        if (!inbound.equals(PickupEta.reportedTrack(train))
            || PickupEta.stopsUntil(Integer.parseInt(row.from), position, inbound) <= 0) return null;
        long arrival = assignedArrival(row, now);
        if (arrival == 0) return null;

        if (watch == null) {
            watch = new TakeWatch();
            takes.put(row.index, watch);
            takeStateDirty = true;
        }
        if (watch.seenAt / 60000 != now / 60000) takeStateDirty = true;
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
                state.put(new JSONObject().put("row", entry.getKey())
                    .put("seenAt", watch.seenAt).put("announcedArrival", watch.announcedArrival)
                    .put("rearmed", watch.rearmed).put("finished", watch.finished));
            } catch (JSONException ignored) {}
        }
        takeStateDirty = false;
        return state.toString();
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
        ServicePlan.Row transfer = plan.transferFor(row);
        long available = transfer == null ? now : plan.at(anchor, transfer.start);
        if (transfer != null && transfer.index > 0) {
            ServicePlan.Row previous = plan.rows.get(transfer.index - 1);
            if ("EST".equals(previous.type) && source.equals(previous.from))
                available = plan.at(anchor, previous.start);
        }
        Option own = null;
        for (Arrival departure : arrivals(source, displacementVia(source, row.from), now)) {
            long travelTime = deadline - departure.at;
            if (departure.train.equals(row.train) && departure.at > now + 5000 && departure.at >= available
                && travelTime > 0 && travelTime < PickupEta.LAP_MS) own = new Option(row.train, departure.at, deadline);
        }
        return own;
    }

    private long assignedArrival(ServicePlan.Row row, long now) {
        JSONObject train = livePosition(row, now);
        if (train != null) pickupEta.observe(row, train, mapReceived);
        PickupEta.Estimate estimate = pickupEta.estimate(row, this, now, train, now - mapReceived, false);
        return estimate.approximate ? 0 : estimate.arrival;
    }

    List<Long> pickupArrivals(ServicePlan.Row row, long now, long maxAge) {
        List<Long> times = new ArrayList<>();
        for (Arrival arrival : arrivals(row.from, row.via, now, maxAge)) {
            if (row.train.equals(arrival.train) && arrival.at > now) times.add(arrival.at);
        }
        if (times.isEmpty() && PickupEta.isTerminal(row.from)) {
            String other = "1".equals(row.via) ? "2" : "1";
            for (Arrival arrival : arrivals(row.from, other, now, maxAge)) {
                if (row.train.equals(arrival.train) && arrival.at > now) times.add(arrival.at);
            }
        }
        return times;
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
            if (arrival > departure.at && arrival < deadline && arrival - departure.at < PickupEta.LAP_MS)
                options.add(new Option(departure.train, departure.at, arrival));
        }
        return options;
    }

    private Board fresh(String stop, long now) {
        return fresh(stop, now, BOARD_FRESH_MS);
    }

    private Board fresh(String stop, long now, long maxBoardAge) {
        Board board = boards.get(stop);
        return board != null && now >= board.received && now - board.received < maxBoardAge ? board : null;
    }

    private List<Arrival> arrivals(String stop, String via, long now) {
        return arrivals(stop, via, now, BOARD_FRESH_MS);
    }

    private List<Arrival> arrivals(String stop, String via, long now, long maxBoardAge) {
        List<Arrival> result = new ArrayList<>();
        Board board = fresh(stop, now, maxBoardAge);
        if (board == null) return result;
        if (!arrivalScopeError(board, stop, false).isEmpty()) return result;
        JSONArray entries = board.data.optJSONArray("via" + via);
        if (entries == null) return result;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null) continue;
            String train = TrainId.fromBoard(entry);
            long at = arrivalTime(entry, board.received);
            if (!train.isEmpty() && at >= now - 15000) result.add(new Arrival(train, at));
        }
        result.sort(Comparator.comparingLong(entry -> entry.at)); return result;
    }

    private static long arrivalTime(JSONObject entry, long received) {
        Object value = entry.opt("arrivalTime");
        if (value instanceof Number && ((Number) value).longValue() > 100000000000L) return ((Number) value).longValue();
        if (value instanceof String) {
            String text = ((String) value).trim();
            try {
                long epoch = Long.parseLong(text);
                if (epoch > 100000000000L) return epoch;
            } catch (NumberFormatException ignored) {}
            try { return Instant.parse(text).toEpochMilli(); } catch (Exception ignored) {}
        }
        double seconds = entry.optDouble("secsLeft", Double.NaN);
        return Double.isFinite(seconds) ? received + Math.round(seconds * 1000) : 0;
    }

    static String displacementVia(String source, String target) {
        try { return Integer.parseInt(target) > Integer.parseInt(source) ? "1" : "2"; } catch (NumberFormatException ignored) { return ""; }
    }
    static String[] stations() { return NAMES.clone(); }
    static String station(String stop) {
        try { int index = Integer.parseInt(stop) - FIRST_STOP; return index >= 0 && index < NAMES.length ? NAMES[index] : stop; } catch (NumberFormatException ignored) { return stop; }
    }
    static int stopPosition(String station) {
        if (station.matches("41[3-9]|42[0-9]|43[0-4]")) return Integer.parseInt(station);
        return STOP_POSITIONS.getOrDefault(stationKey(station), 0);
    }
    private static String stationKey(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private static String clock(long time) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ServicePlan.ZONE.toZoneId()).format(Instant.ofEpochMilli(time)); }
}
