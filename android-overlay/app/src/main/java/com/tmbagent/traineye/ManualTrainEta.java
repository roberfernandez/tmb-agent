package com.tmbagent.traineye;

import org.json.JSONObject;

/** Follows the next pass of a manually selected train at the waiting station. */
final class ManualTrainEta {
    final String stop;
    private final String train;
    private final int destination;
    private String approachingVia = "";
    private boolean arrived;

    ManualTrainEta(String stop, String train, boolean arrived) {
        this.stop = stop;
        this.train = TrainId.normalize(train);
        this.destination = Integer.parseInt(stop);
        this.arrived = arrived;
    }

    boolean isWaiting() { return !arrived; }

    boolean confirm() {
        if (arrived) return false;
        arrived = true;
        return true;
    }

    boolean observe(JSONObject position) {
        if (arrived || position == null) return false;
        int station = ServiceAlerts.stopPosition(position.optString("estacion", ""));
        if (station == 0) return false;
        if (station == destination) return confirm();

        String via = PickupEta.reportedTrack(position);
        int distance = PickupEta.stopsUntil(destination, station, via);
        if (distance == 0) return false;
        if (distance < 0 && (via.equals(approachingVia)
            || (!approachingVia.isEmpty() && PickupEta.isTerminal(stop) && distance >= -3))) return confirm();
        approachingVia = distance > 0 && distance <= 3 ? via : "";
        return false;
    }

    long predictedArrival(ServiceAlerts boards, long now, JSONObject liveTrain, long positionAgeMs) {
        return arrived ? 0 : PickupEta.liveArrival(boards, stop, "", train, now, liveTrain, positionAgeMs);
    }
}
