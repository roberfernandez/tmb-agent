package cat.tmbagent.miralincorporatiu;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Local countdown to the official SORTIDA (departure), not a measured arrival.
 * TMB supplies HH:mm precision only. No independent movement predictions.
 */
final class OfficialDepartureCountdown {
    private static final ZoneId BARCELONA = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final long MAX_AHEAD_SECONDS = 8 * 60 * 60;
    private static final long PAST_GRACE_SECONDS = 60;

    private OfficialDepartureCountdown() {}

    /** -1 means stale or invalid scheduled time, never a fabricated ETA. */
    static long departureEpochMs(String scheduled, long nowEpochMs) {
        if (scheduled == null || !scheduled.trim().matches("\\d{2}:\\d{2}")) return -1;
        try {
            LocalTime scheduledTime = LocalTime.parse(scheduled.trim(), HH_MM);
            ZonedDateTime now = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(nowEpochMs), BARCELONA);
            ZonedDateTime target = ZonedDateTime.ofLocal(
                LocalDateTime.of(now.toLocalDate(), scheduledTime), BARCELONA, now.getOffset());
            long seconds = Duration.between(now.toInstant(), target.toInstant()).getSeconds();
            if (seconds < -PAST_GRACE_SECONDS) {
                // 00:03 in a board read at 23:58 means tomorrow, not 23 h ago.
                if (now.getHour() < 16 || scheduledTime.getHour() >= 12) return -1;
                target = target.plusDays(1);
                seconds = Duration.between(now.toInstant(), target.toInstant()).getSeconds();
            }
            return seconds <= MAX_AHEAD_SECONDS ? target.toInstant().toEpochMilli() : -1;
        } catch (DateTimeParseException ex) {
            return -1;
        }
    }

    static String display(long departureEpochMs, long nowEpochMs) {
        if (departureEpochMs < 0) return "--";
        long remaining = Math.max(0L, (departureEpochMs - nowEpochMs + 999L) / 1000L);
        if (remaining > MAX_AHEAD_SECONDS) return "--";
        // ~ means a scheduled *departure*, not a guaranteed platform arrival.
        if (remaining >= 3600) {
            return String.format(Locale.ROOT, "~%dh%02d", remaining / 3600, (remaining % 3600) / 60);
        }
        return String.format(Locale.ROOT, "~%d'%02d''", remaining / 60, remaining % 60);
    }
}
