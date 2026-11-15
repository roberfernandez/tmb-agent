package com.tmbagent.traineye;

import org.json.JSONObject;

/** Continuous manual watch: the selected train may pass the same station again. */
final class ManualTrainEta {
    final String stop;
    final String via;
    private final String train;
    private final int destination;
    private boolean atStation;

    // The former fourth argument recorded a permanently completed first arrival.
    // It is deliberately ignored: the user follows a train, not just its first pass.
    ManualTrainEta(String stop, String via, String train, boolean legacyArrived) {
        if (stop == null || !stop.matches("41[3-9]|42[0-9]|43[0-4]") ||
            (!PickupEta.isTerminal(stop) && !"1".equals(via) && !"2".equals(via))) {
            throw new IllegalArgumentException("A manual station requires a selected track");
        }
        this.stop = stop;
        this.via = PickupEta.isTerminal(stop) ? "" : via;
        this.train = TrainId.normalize(train);
        this.destination = Integer.parseInt(stop);
    }

    /** Keep the station subscribed for every successive circuit. */
    boolean isWaiting() { return true; }

    /** Detect arrival events without permanently completing the manual watch. */
    boolean observe(JSONObject position) {
        if (position == null) return false;
        int station = ServiceAlerts.stopPosition(position.optString("estacion", ""));
        if (station == 0) return false;
        String observedVia = PickupEta.reportedTrack(position);
        boolean onSelectedPlatform = PickupEta.isTerminal(stop) || via.equals(observedVia);
        boolean here = station == destination && onSelectedPlatform;
        boolean arrivedNow = here && !atStation;
        atStation = here;
        return arrivedNow;
    }

    long predictedArrival(ServiceAlerts boards, long now, JSONObject liveTrain, long positionAgeMs) {
        // No countdown while the train is already standing on the selected platform.
        // Once it departs, the next valid station-board prediction is displayed.
        return atStation ? 0 : PickupEta.liveArrival(boards, stop, via, train, now, liveTrain, positionAgeMs);
    }
}
