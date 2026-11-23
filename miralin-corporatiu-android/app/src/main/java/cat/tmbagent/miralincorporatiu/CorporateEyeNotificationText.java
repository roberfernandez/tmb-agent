package cat.tmbagent.miralincorporatiu;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Presentation of existing corporate motor state; never pulls or invents train data. */
final class CorporateEyeNotificationText {
    final String title;
    final String body;

    private CorporateEyeNotificationText(String title, String body) {
        this.title = title;
        this.body = body;
    }

    static CorporateEyeNotificationText forState(boolean manual, String rowType, String train,
        String currentStation, String targetStation, String origin, String destination,
        int via, long projectedAt, long now, boolean stale) {
        String id = isTrain(train) ? train : "—";
        String platform = via == 1 || via == 2 ? " · Via " + via : "";
        String heading;
        String content;
        if (manual) {
            heading = id + " · " + nonempty(currentStation, targetStation) + platform;
            content = targetStation + " " + eta(projectedAt, now);
        } else if ("TRA".equals(rowType)) {
            heading = "TRA · " + origin + " → " + destination;
            content = isTrain(train) ? id + platform + " · " + eta(projectedAt, now)
                : "Trasllat" + platform + " · sense tren assignat";
        } else if ("CON".equals(rowType)) {
            heading = "CON · " + id + " · " + nonempty(currentStation, targetStation) + platform;
            content = "Final " + destination + " · " + eta(projectedAt, now);
        } else {
            heading = "EST · " + nonempty(origin, targetStation) + platform;
            content = isTrain(train) ? id + " " + eta(projectedAt, now)
                : "Esperant el proper tren";
        }
        if (stale) content += " · dades anteriors";
        return new CorporateEyeNotificationText(heading.trim(), content.trim());
    }

    private static boolean isTrain(String id) {
        return id != null && id.matches("4[0-2][0-9]");
    }

    private static String nonempty(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "—".equals(value)
            ? fallback : value;
    }

    private static String eta(long at, long now) {
        if (at <= now || at <= 0) return "sense previsió";
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", new Locale("ca", "ES"));
        format.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
        return "≈" + format.format(new Date(at)) + " · en "
            + Math.max(1, (int) Math.ceil((at - now) / 60000d)) + " min";
    }
}
