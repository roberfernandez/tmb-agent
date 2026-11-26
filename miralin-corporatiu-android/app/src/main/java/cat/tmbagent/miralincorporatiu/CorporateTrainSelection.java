package cat.tmbagent.miralincorporatiu;

import java.util.List;
import java.util.Locale;

/**
 * Selects one train from the official corporate L4 HTML board.
 * All values are copied as published: "Temps" is NOT an ETA.
 */
final class CorporateTrainSelection {
    private CorporateTrainSelection() {}

    static final class Train {
        final String id;
        final String departure;
        final String previousTrainGap;
        final String station;
        final String track;

        Train(String id, String departure, String previousTrainGap, String station, String track) {
            this.id = id;
            this.departure = departure;
            this.previousTrainGap = previousTrainGap;
            this.station = station;
            this.track = track;
        }
    }

    static Train find(CorporateBoardParser.Board board, String selectedId) {
        if (board == null || selectedId == null) return null;
        String sought = selectedId.trim().toUpperCase(Locale.ROOT);
        if (sought.isEmpty()) return null;
        Train onFirst = findOnTrack(board.via1, sought);
        return onFirst != null ? onFirst : findOnTrack(board.via2, sought);
    }

    /** Selected stop's direction/platform. A train may occur on BOTH boards at different times. */
    static Train find(CorporateBoardParser.Board board, String selectedId, int targetVia) {
        if (board == null || selectedId == null || (targetVia != 1 && targetVia != 2)) return null;
        String sought = selectedId.trim().toUpperCase(Locale.ROOT);
        if (sought.isEmpty()) return null;
        return findOnTrack(targetVia == 1 ? board.via1 : board.via2, sought);
    }

    static String canonicalId(String id) {
        return id.matches("[MP][0-9]{2}") ? "4" + id.substring(1) : id;
    }

    private static Train findOnTrack(CorporateBoardParser.Track track, String sought) {
        if (track == null || !track.present) return null;
        String canonical = canonicalId(sought);
        Train alias = null;
        for (List<String> cells : track.rows) {
            if (cells.size() < 4) continue;
            String id = cells.get(0).trim().toUpperCase(Locale.ROOT);
            if (!canonicalId(id).equals(canonical)) continue;
            String via = cells.size() > 4 && !cells.get(4).trim().isEmpty()
                ? cells.get(4).trim() : "Via " + track.number;
            Train match = new Train(id, cells.get(1), cells.get(2), cells.get(3), via);
            if (id.equals(sought)) return match;
            if (alias == null) alias = match;
        }
        return alias;
    }
}
