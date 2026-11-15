package com.tmbagent.traineye;

import org.json.JSONObject;

/** Follows the next pass of a manually selected train at the waiting station. */
final class ManualTrainEta {
    final String stop;
    final String via;
    private final String train;
    private final int destination;
    private String approachingVia = "";
    private boolean arrived;

    ManualTrainEta(String stop, String via, String train, boolean arrived) {
        if (!stop.matches("41[3-9]|42[0-9]|43[0-4]") ||
            (!PickupEta.isTerminal(stop) && !"1".equals(via) && !"2".equals(via))) {
            throw new IllegalArgumentException("A manual station requires a selected track");
        }
        this.stop = stop;
        this.via = PickupEta.isTerminal(stop) ? "" : via;
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
        String observedVia = PickupEta.reportedTrack(position);
        if (station == destination) {
            return (PickupEta.isTerminal(stop) || via.equals(observedVia)) && confirm();
        }
        if (!PickupEta.isTerminal(stop) && !via.equals(observedVia)) {
            approachingVia = "";
            return false;
        }
        int distance = PickupEta.stopsUntil(destination, station, observedVia);
        if (distance == 0) return false;
        if (distance < 0 && (observedVia.equals(approachingVia)
            || (!approachingVia.isEmpty() && PickupEta.isTerminal(stop) && distance >= -3))) return confirm();
        approachingVia = distance > 0 && distance <= 3 ? observedVia : "";
        return false;
    }

    long predictedArrival(ServiceAlerts boards, long now, JSONObject liveTrain, long positionAgeMs) {
        return arrived ? 0 : PickupEta.liveArrival(boards, stop, via, train, now, liveTrain, positionAgeMs);
    }
}
