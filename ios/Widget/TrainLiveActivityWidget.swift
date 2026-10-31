import ActivityKit
import SwiftUI
import WidgetKit

struct TrainLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TrainActivityAttributes.self) { context in
            LockScreenActivityView(state: context.state, activityIsStale: context.isStale)
                .activityBackgroundTint(Color(red: 0.04, green: 0.10, blue: 0.18))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text("L4")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.yellow)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text("Tren \(context.state.train)")
                        .font(.headline)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("V\(context.state.track)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.yellow)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(context.state.station)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        freshness(context.state, activityIsStale: context.isStale)
                    }
                }
            } compactLeading: {
                Text(context.state.train)
                    .font(.caption2.weight(.bold))
            } compactTrailing: {
                Text("V\(context.state.track)")
                    .font(.caption2)
            } minimal: {
                Text("L4")
                    .font(.caption2.weight(.bold))
            }
            .keylineTint(.yellow)
        }
    }
}

private struct LockScreenActivityView: View {
    let state: TrainActivityAttributes.ContentState
    let activityIsStale: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text(state.train)
                .font(.title2.weight(.heavy))
                .foregroundStyle(.black)
                .frame(width: 54, height: 54)
                .background(.yellow, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 3) {
                Text(state.station)
                    .font(.headline)
                    .lineLimit(1)
                Text("L4 · via \(state.track) · \(state.status)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                freshness(state)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 5)
    }
}

@ViewBuilder
private func freshness(_ state: TrainActivityAttributes.ContentState, activityIsStale: Bool = false) -> some View {
    if state.stale || activityIsStale {
        Text("Dades antigues · darrera recepció")
            .font(.caption2)
            .foregroundStyle(.orange)
    } else {
        HStack(spacing: 3) {
            Text("Actualitzat")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(Date(timeIntervalSince1970: state.updatedAt), style: .timer)
                .font(.caption2.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }
}

@main
struct TrainLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        TrainLiveActivityWidget()
    }
}
