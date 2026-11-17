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
        final String train;
        final int stop;
        final int via;
        final boolean driving;
        Selection(String train, int stop, int via, boolean driving) {
            this.train = train;
            this.stop = stop;
            this.via = via;
            this.driving = driving;
        }
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

    private static String stationName(String code) {
        final String[] names = {"La Pau","Besòs","Besòs Mar","El Maresme · Fòrum",
            "Selva de Mar","Poblenou","Llacuna","Bogatell","Ciutadella · Vila Olímpica",
            "Barceloneta","Jaume I","Urquinaona","Passeig de Gràcia","Girona",
            "Verdaguer","Joanic","Alfons X","Guinardó · Hospital de Sant Pau",
            "Maragall","Llucmajor","Via Júlia","Trinitat Nova"};
        if (!validStop(code)) return code;
        return names[Integer.parseInt(code) - FIRST_STOP];
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

    private long minuteMillis(int minute) {
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
        int stop = Integer.parseInt(driving ? chosen.to : chosen.from);
        // At terminal stations a single physical platform is shared. 0 means
        // "select whichever of the two corporate tables contains the train".
        int via = stop == FIRST_STOP || stop == LAST_STOP ? 0
            : Integer.parseInt(driving && validVia(chosen.out) ? chosen.out : chosen.via);
        return new Selection(chosen.train, stop, via, driving);
    }
}
