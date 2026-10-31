import ActivityKit
import Foundation

struct TrainActivityAttributes: ActivityAttributes {
    var mode: String
    var serviceDate: String
    var startedAt: TimeInterval

    struct ContentState: Codable, Hashable {
        var train: String
        var station: String
        var track: String
        var status: String
        var destination: String
        var updatedAt: TimeInterval
        var stale: Bool
    }
}
