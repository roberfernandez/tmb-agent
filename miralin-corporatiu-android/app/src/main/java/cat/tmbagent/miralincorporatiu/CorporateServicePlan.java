package cat.tmbagent.miralincorporatiu;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable work-day schedule handed off by El meu servei over an Android deep link.
 * Reads the existing "minute,minute,TRA|EST|CON,origin,destination,train,via,out" format.
 * Does not contact any external service and does not store the worker's schedule remotely.
 */
final class CorporateServicePlan {
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final int FIRST_STOP = 413, LAST_STOP = 434;

    static final class Segment {
        final int start, end;
        final String type, from, to, train, via, out;
        Segment(String[] p) {
            start = Integer.parseInt(p[0]);
            end = Integer.parseInt(p[1]);
            type = p[2];
            from = p[3]; to = p[4];
            train = p[5]; via = p[6]; out = p[7];
        }
        boolean driving() { return "CON".equals(type); }
    }

    static final class Selection {
        static final int IDLE = 0;
        static final int PICKUP = 1;
        static final int DRIVING = 2;
        static final int WAIT_TRANSFER = 3;
        static final int TRANSFER_SEARCH = 4;
        static final int TRANSFER_TRACK = 5;

        final String train;
        final int stop;
        final int via;
        final boolean driving;
        final long takeoverAt;
        final int kind;
        final int row;
        final int origin;
        final int destination;

        Selection(String train, int stop, int via, boolean driving, long takeoverAt,
                  int kind, int row, int origin, int destination) {
            this.train = train == null ? "" : train;
            this.stop = stop;
            this.via = via;
            this.driving = driving;
            this.takeoverAt = takeoverAt;
            this.kind = kind;
            this.row = row;
            this.origin = origin;
            this.destination = destination;
        }

        boolean transfer() {
            return kind == WAIT_TRANSFER || kind == TRANSFER_SEARCH || kind == TRANSFER_TRACK;
        }
        boolean transferWaiting() { return kind == WAIT_TRANSFER; }
        boolean transferSearching() { return kind == TRANSFER_SEARCH; }
    }

    private final LocalDate date;
    private final List<Segment> segments = new ArrayList<>();

    CorporateServicePlan(String dateText, String schedule) {
        if (dateText == null || schedule == null || schedule.length() > 16000) {
            throw new IllegalArgumentException("Data o horari del servei no vàlids.");
        }
        try { date = LocalDate.parse(dateText); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Data del servei incorrecta."); }
        for (String entry : schedule.split(";")) {
            String[] p = entry.split(",", -1);
            if (p.length != 8) continue;
            try {
                Segment segment = new Segment(p);
                if (segment.start < 0 || segment.end <= segment.start || segment.end > 2879
                    || !("CON".equals(segment.type) || "EST".equals(segment.type) || "TRA".equals(segment.type))
                    || !validStop(segment.from) || !validStop(segment.to)) continue;
                if (segment.driving() && (!validTrain(segment.train)
                    || !validVia(segment.via) || !(validVia(segment.out) || "RET".equals(segment.out)))) continue;
                segments.add(segment);
            } catch (NumberFormatException ignored) {}
        }
        if (segments.stream().noneMatch(Segment::driving))
            throw new IllegalArgumentException("L'horari no conté cap tram de conducció vàlid.");
    }

    static boolean validStop(String value) {
        if (value == null || !value.matches("4\\d{2}")) return false;
        int number = Integer.parseInt(value);
        return number >= FIRST_STOP && number <= LAST_STOP;
    }

    static boolean validTrain(String value) { return value != null && value.matches("4\\d{2}"); }

    static boolean validVia(String value) { return "1".equals(value) || "2".equals(value); }

    static boolean terminal(int stop) { return stop == FIRST_STOP || stop == LAST_STOP; }

    /**
     * L4 station codes follow the La Pau → Trinitat Nova direction.
     * On the corporate boards, Via 1 is that direction and Via 2 is the reverse.
     */
    static int directionVia(String from, String to) {
        if (!validStop(from) || !validStop(to) || from.equals(to)) return 0;
        return Integer.parseInt(to) > Integer.parseInt(from) ? 1 : 2;
    }

    static final class Notice {
        final String key, title, body;
        Notice(String key, String title, String body) {
            this.key = key; this.title = title; this.body = body;
        }
    }

    /** Scheduled transfer and pickup reminders; no claim of measured train arrival. */
    List<Notice> noticesAt(long nowMillis, int marginMinutes) {
        List<Notice> due = new ArrayList<>();
        int margin = Math.max(1, Math.min(120, marginMinutes));
        for (int i = 0; i < segments.size(); i++) {
            Segment row = segments.get(i);
            if (!row.driving() && !"TRA".equals(row.type)) continue;
            long start = minuteMillis(row.start);
            if (nowMillis < start - margin * 60_000L || nowMillis >= start + 60_000L) continue;
            String fromName = stationName(row.from), toName = stationName(row.to);
            if (row.driving()) due.add(new Notice("take-" + i,
                "Presa del tren " + row.train,
                fromName + " · via " + row.via + " · hora prevista " + formatMinute(row.start)));
            else due.add(new Notice("transfer-" + i,
                "Trasllat del servei",
                fromName + " → " + toName + " · inici " + formatMinute(row.start)));
        }
        return due;
    }

    private static String formatMinute(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    static String stationName(String code) {
        final String[] names = {"La Pau","Besòs","Besòs Mar","El Maresme · Fòrum",
            "Selva de Mar","Poblenou","Llacuna","Bogatell","Ciutadella · Vila Olímpica",
            "Barceloneta","Jaume I","Urquinaona","Passeig de Gràcia","Girona",
            "Verdaguer","Joanic","Alfons X","Guinardó · Hospital de Sant Pau",
            "Maragall","Llucmajor","Via Júlia","Trinitat Nova"};
        if (!validStop(code)) return code;
        return names[Integer.parseInt(code) - FIRST_STOP];
    }

    /** Rows are kept in the same order as the original uploaded service image. */
    List<Segment> viewerSegments() { return java.util.Collections.unmodifiableList(segments); }

    /** Active row for the compact photo viewer, falling back to the next row before duty. */
    int viewerIndex(long nowMillis) {
        int next = -1;
        for (int i = 0; i < segments.size(); i++) {
            Segment row = segments.get(i);
            long start = minuteMillis(row.start), end = minuteMillis(row.end);
            if (start <= nowMillis && nowMillis < end) return i;
            if (next == -1 && start > nowMillis) next = i;
        }
        return next;
    }

    /** Shared stable key used to prevent applying another uploaded day's live state. */
    String webKey() {
        StringBuilder raw = new StringBuilder(date.toString()).append('|');
        for (int i = 0; i < segments.size(); i++) {
            Segment r = segments.get(i);
            if (i > 0) raw.append(';');
            raw.append(r.start).append(',').append(r.end).append(',')
                .append(r.type).append(',').append(r.from).append(',')
                .append(r.to).append(',').append(r.train).append(',')
                .append(r.via).append(',').append(r.out);
        }
        return Integer.toHexString(raw.toString().hashCode());
    }

    /** A selected driving row can still be awaiting pickup after its scheduled start. */
    Selection selectionFor(int rowIndex, boolean driving) {
        if (rowIndex < 0 || rowIndex >= segments.size()) return null;
        Segment chosen = segments.get(rowIndex);
        if (!chosen.driving()) return null;
        int selectedStop = Integer.parseInt(driving ? chosen.to : chosen.from);
        // Terminal platforms can be represented in both corporate tables.
        // Keep terminal lookups unqualified and let the unique SORTIDA row
        // establish an unambiguous passage; intermediate stations use via.
        int selectedVia = terminal(selectedStop) ? 0
            : Integer.parseInt(driving && validVia(chosen.out) ? chosen.out : chosen.via);
        return new Selection(chosen.train, selectedStop, selectedVia, driving,
            minuteMillis(chosen.start), driving ? Selection.DRIVING : Selection.PICKUP,
            rowIndex, Integer.parseInt(chosen.from), Integer.parseInt(chosen.to));
    }

    Selection transferSelection(int rowIndex, String selectedTrain, boolean started) {
        if (rowIndex < 0 || rowIndex >= segments.size()) return null;
        Segment row = segments.get(rowIndex);
        if (!"TRA".equals(row.type) || row.from.equals(row.to)) return null;
        int origin = Integer.parseInt(row.from), destination = Integer.parseInt(row.to);
        int via = directionVia(row.from, row.to);
        if (!started) {
            return new Selection("", origin, via, false, minuteMillis(row.start),
                Selection.WAIT_TRANSFER, rowIndex, origin, destination);
        }
        if (selectedTrain == null || selectedTrain.isEmpty()) {
            return new Selection("", origin, via, false, -1,
                Selection.TRANSFER_SEARCH, rowIndex, origin, destination);
        }
        return new Selection(selectedTrain, destination, via, false, -1,
            Selection.TRANSFER_TRACK, rowIndex, origin, destination);
    }

    Selection idleSelection(int rowIndex, long countdownAt) {
        if (rowIndex < 0 || rowIndex >= segments.size()) return null;
        Segment row = segments.get(rowIndex);
        return idleAtStopSelection(rowIndex, Integer.parseInt(row.from), countdownAt);
    }

    Selection idleAtStopSelection(int rowIndex, int stop, long countdownAt) {
        if (rowIndex < 0 || rowIndex >= segments.size() || stop < FIRST_STOP || stop > LAST_STOP) return null;
        Segment row = segments.get(rowIndex);
        return new Selection("", stop, 0, false, countdownAt,
            Selection.IDLE, rowIndex, stop, Integer.parseInt(row.to));
    }

    String identity() {
        StringBuilder data = new StringBuilder(date.toString());
        for (Segment row : segments) {
            data.append('|').append(row.start).append(',').append(row.end).append(',')
                .append(row.type).append(',').append(row.from).append(',')
                .append(row.to).append(',').append(row.train).append(',')
                .append(row.via).append(',').append(row.out);
        }
        return Integer.toHexString(data.toString().hashCode());
    }

    long minuteMillis(int minute) {
        return date.atStartOfDay(ZONE).plusMinutes(minute).toInstant().toEpochMilli();
    }

    /**
     * Before the next CON: track the upcoming boarding train at its origin platform.
     * While driving: track the same train toward the destination platform.
     * After the service ends: no selection (never follow yesterday's last train).
     */
    Selection at(long nowMillis) {
        Segment activeDriving = null, nextDriving = null;
        long nextStart = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        for (Segment row : segments) {
            if (!row.driving()) continue;
            long start = minuteMillis(row.start);
            long finish = minuteMillis(row.end);
            end = Math.max(end, finish);
            if (start <= nowMillis && nowMillis < finish) activeDriving = row;
            else if (start > nowMillis && start < nextStart) {
                nextDriving = row;
                nextStart = start;
            }
        }
        if (nowMillis >= end) return null;
        Segment chosen = activeDriving != null ? activeDriving : nextDriving;
        if (chosen == null) return null;
        boolean driving = chosen == activeDriving;
        return selectionFor(segments.indexOf(chosen), driving);
    }
}
