package com.tmbagent.traineye;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the official selected-stop T.ANT cells; never a page's hidden inputs. */
final class OfficialHeadwayBoard {
    static final long FRESH_MS = 60_000L;
    static final String HOST = "aplicacions.tmb.cat";
    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter PAGE_CLOCK = DateTimeFormatter.ofPattern("d/M/uuuu H:mm", Locale.ROOT);

    private static final class Entry {
        final String train, via;
        final long interval;
        final int arrivalMinute;
        Entry(String train, String via, long interval, int arrivalMinute) {
            this.train = train; this.via = via; this.interval = interval; this.arrivalMinute = arrivalMinute;
        }
    }

    final String stop, serverDate, serverClock;
    final long receivedAt, serverAt;
    private final List<Entry> entries = new ArrayList<>();

    private OfficialHeadwayBoard(String stop, String date, String clock, long now) {
        this.stop = stop; serverDate = date; serverClock = clock; receivedAt = now;
        serverAt = LocalDateTime.parse(date + " " + clock, PAGE_CLOCK)
            .atZone(ServicePlan.ZONE.toZoneId()).toInstant().toEpochMilli();
        if (now - serverAt > 120_000L || serverAt - now > 60_000L)
            throw new IllegalArgumentException("El quadre de TMB no està actualitzat.");
    }

    static boolean allowsUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value).normalize();
            return "https".equalsIgnoreCase(uri.getScheme())
                && HOST.equalsIgnoreCase(uri.getHost()) && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getPath() != null && uri.getPath().startsWith("/miralin/");
        } catch (Exception ignored) { return false; }
    }

    static OfficialHeadwayBoard parse(JSONObject snapshot, String expectedStop, long now) {
        if (!expectedStop.matches("41[3-9]|42[0-9]|43[0-4]")
            || !"4".equals(snapshot.optString("line")) || !expectedStop.equals(snapshot.optString("stop"))
            || !snapshot.optBoolean("ready", false))
            throw new IllegalArgumentException("Esperant el quadre oficial de l'estació.");
        OfficialHeadwayBoard result = new OfficialHeadwayBoard(expectedStop,
            snapshot.optString("date"), snapshot.optString("clock"), now);
        int pageMinute = minute(result.serverClock);
        JSONArray tables = snapshot.optJSONArray("tables");
        if (tables == null) throw new IllegalArgumentException("No hi ha les taules de TMB.");
        for (int i = 0; i < tables.length(); i++) {
            JSONObject table = tables.optJSONObject(i);
            if (table == null) continue;
            String via = table.optString("via");
            if (!"1".equals(via) && !"2".equals(via)) continue;
            Matcher rows = ROW.matcher(table.optString("html"));
            while (rows.find()) {
                List<String> cells = new ArrayList<>();
                Matcher values = CELL.matcher(rows.group(1));
                while (values.find()) cells.add(text(values.group(1)));
                if (cells.size() != 5) continue;
                String train = TrainId.normalize(cells.get(0));
                int arrival = minute(cells.get(1));
                if (train.isEmpty() || arrival < 0) continue;
                if (arrival < pageMinute - 1) arrival += 1440;
                result.entries.add(new Entry(train, via, interval(cells.get(2)), arrival));
            }
        }
        return result;
    }

    long seconds(String train, String via, long now) {
        if (now < receivedAt || now - receivedAt >= FRESH_MS || now - serverAt > 120_000L) return -1;
        train = TrainId.normalize(train);
        boolean restrict = !PickupEta.isTerminal(stop) && ("1".equals(via) || "2".equals(via));
        Entry best = null;
        for (Entry entry : entries) {
            if (!train.equals(entry.train) || restrict && !via.equals(entry.via)) continue;
            if (best == null || entry.arrivalMinute < best.arrivalMinute) best = entry;
        }
        return best == null ? -1 : best.interval;
    }

    static String format(long seconds) {
        if (seconds < 0) return "--";
        return seconds >= 3600
            ? String.format(Locale.ROOT, "%dh%02d", seconds / 3600, seconds % 3600 / 60)
            : String.format(Locale.ROOT, "%d′%02d″", seconds / 60, seconds % 60);
    }

    static long interval(String value) {
        if (value == null) return -1;
        String[] parts = value.trim().split(":", -1);
        if (parts.length != 2 && parts.length != 3) return -1;
        try {
            for (String part : parts) if (!part.matches("\\d{1,5}")) return -1;
            long seconds = Long.parseLong(parts[parts.length - 1]);
            long minutes = Long.parseLong(parts[parts.length - 2]);
            if (seconds > 59 || parts.length == 3 && minutes > 59) return -1;
            return seconds + minutes * 60 + (parts.length == 3 ? Long.parseLong(parts[0]) * 3600 : 0);
        } catch (NumberFormatException ignored) { return -1; }
    }

    private static int minute(String value) {
        if (!value.matches("\\d{1,2}:[0-5]\\d")) return -1;
        String[] parts = value.split(":");
        int hour = Integer.parseInt(parts[0]);
        return hour > 23 ? -1 : hour * 60 + Integer.parseInt(parts[1]);
    }

    private static String text(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").replace("&#160;", " ")
            .replace("&amp;", "&").trim();
    }
}
