package com.tmbagent.traineye;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Shared schedule selection for launch, ongoing tracking and service alerts. */
final class ServicePlan {
    static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Madrid");
    static boolean isTrain(String train) { return train != null && train.matches("40[1-9]|41[0-7]"); }

    static final class Row {
        final int start, end, index;
        final String type, from, to, train, via, out;
        Row(String[] p, int index) {
            this.start = Integer.parseInt(p[0]); this.end = Integer.parseInt(p[1]); this.index = index;
            type = p.length == 3 ? "CON" : p[2];
            from = p.length == 3 ? "" : p[3]; to = p.length == 3 ? "" : p[4];
            train = p.length == 3 ? p[2] : p[5]; via = p.length == 3 ? "" : p[6]; out = p.length == 3 ? "" : p[7];
        }
        boolean driving() { return "CON".equals(type) && isTrain(train); }
    }
    final List<Row> rows = new ArrayList<>();
    ServicePlan(String schedule) {
        for (String entry : (schedule == null ? "" : schedule).split(";")) {
            String[] p = entry.split(",", -1);
            if (p.length != 3 && p.length != 8) continue;
            try {
                Row row = new Row(p, rows.size());
                if (row.start >= 0 && row.end > row.start && row.end <= 2879) rows.add(row);
            } catch (NumberFormatException ignored) {}
        }
    }
    int minutesAt(long time) {
        Calendar clock = Calendar.getInstance(ZONE); clock.setTimeInMillis(time);
        int now = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE);
        int first = Integer.MAX_VALUE, end = -1;
        for (Row row : rows) { first = Math.min(first, row.start); end = Math.max(end, row.end); }
        if (end > 1440 && now < first && now < end % 1440) now += 1440;
        return now;
    }
    long anchorAt(long time) {
        Calendar day = Calendar.getInstance(ZONE); day.setTimeInMillis(time);
        int clockMinutes = day.get(Calendar.HOUR_OF_DAY) * 60 + day.get(Calendar.MINUTE);
        if (minutesAt(time) > clockMinutes) day.add(Calendar.DATE, -1);
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0); day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }
    long at(long anchor, int minutes) {
        Calendar time = Calendar.getInstance(ZONE); time.setTimeInMillis(anchor);
        time.add(Calendar.MINUTE, minutes); return time.getTimeInMillis();
    }
    String trainAt(long time, String fallback) {
        int now = minutesAt(time), nearest = Integer.MAX_VALUE, latest = -1;
        String next = null, last = null;
        for (Row row : rows) {
            if (!row.driving()) continue;
            if (now >= row.start && now < row.end) return row.train;
            if (row.start > now && row.start < nearest) { nearest = row.start; next = row.train; }
            if (row.end > latest) { latest = row.end; last = row.train; }
        }
        return next != null ? next : last != null ? last : fallback;
    }
    Row nextDriving(long time, long anchor) {
        for (Row row : rows) if (row.driving() && at(anchor, row.start) + 15 * 60000L > time) return row;
        return null;
    }
    String sourceFor(Row row) {
        if (row.index == 0) return "";
        Row previous = rows.get(row.index - 1);
        return "TRA".equals(previous.type) && previous.to.equals(row.from) ? previous.from : "";
    }
}

