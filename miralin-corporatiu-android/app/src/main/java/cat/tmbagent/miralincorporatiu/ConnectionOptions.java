package cat.tmbagent.miralincorporatiu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Match published departures of the same train at both stations. */
final class ConnectionOptions {
    static final class Option {
        final String train, previousTrainGap;
        final long departure, margin;
        Option(String train, long departure, long margin, String previousTrainGap) {
            this.train = train;
            this.departure = departure;
            this.margin = margin;
            this.previousTrainGap = previousTrainGap;
        }
    }

    static List<Option> calculate(CorporateBoardParser.Board origin, CorporateBoardParser.Board destination,
                                  String relay, int via, long plannedRelay, long now) {
        List<Option> options = new ArrayList<>();
        CorporateTrainSelection.Train handover = CorporateTrainSelection.find(destination, relay, 3 - via);
        if (handover == null) return options;
        long deadline = OfficialDepartureCountdown.departureEpochMs(handover.departure, now);
        if (deadline <= now || CorporateProjection.project(deadline, now, plannedRelay).laps != 0)
            return options;
        CorporateBoardParser.Track platform = via == 1 ? origin.via1 : origin.via2;
        Set<String> seen = new HashSet<>();
        for (List<String> cells : platform.rows) {
            if (cells.size() < 4) continue;
            String id = CorporateTrainSelection.canonicalId(cells.get(0).trim().toUpperCase(Locale.ROOT));
            if (!CorporateServicePlan.validTrain(id) || !seen.add(id) || id.equals(relay)) continue;
            CorporateTrainSelection.Train start = CorporateTrainSelection.find(origin, id, via);
            CorporateTrainSelection.Train end = CorporateTrainSelection.find(destination, id, via);
            if (start == null || end == null) continue;
            long departure = OfficialDepartureCountdown.departureEpochMs(start.departure, now);
            long passage = OfficialDepartureCountdown.departureEpochMs(end.departure, now);
            if (departure > now && passage > departure && passage < deadline)
                options.add(new Option(id, departure, deadline - passage, start.previousTrainGap));
        }
        options.sort(Comparator.comparingLong(option -> option.departure));
        return options.subList(Math.max(0, options.size() - 3), options.size());
    }
}
