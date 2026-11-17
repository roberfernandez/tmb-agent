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

    private static Train findOnTrack(CorporateBoardParser.Track track, String sought) {
        if (track == null || !track.present) return null;
        for (List<String> cells : track.rows) {
            if (cells.size() < 4) continue;
            String id = cells.get(0).trim().toUpperCase(Locale.ROOT);
            if (!id.equals(sought)) continue;
            String via = cells.size() > 4 && !cells.get(4).trim().isEmpty()
                ? cells.get(4).trim() : "Via " + track.number;
            return new Train(id, cells.size() > 1 ? cells.get(1) : "",
                cells.size() > 2 ? cells.get(2) : "", cells.get(3), via);
        }
        return null;
    }
}
