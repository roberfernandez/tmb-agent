import ActivityKit
import SwiftUI

private let apiBase = URL(string: "https://tmb-servei-proves.onrender.com/api/")!

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var mode = "manual"
    @State private var train = "403"
    @State private var serviceDate = ""
    @State private var schedule = ""
    @State private var statusMessage = "Tria una opció i inicia la Live Activity."
    @State private var isStarting = false
    @State private var activityID: String?
    @State private var tokenTask: Task<Void, Never>?
    @State private var pollingTask: Task<Void, Never>?
    @State private var lastState: TrainActivityAttributes.ContentState?
    @State private var lastPublishedAt: Date = .distantPast

    private var activitiesEnabled: Bool {
        ActivityAuthorizationInfo().areActivitiesEnabled
    }

    private var runningActivity: Activity<TrainActivityAttributes>? {
        if let activityID {
            return Activity<TrainActivityAttributes>.activities.first { $0.id == activityID }
        }
        return Activity<TrainActivityAttributes>.activities.first
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Seguiment") {
                    Picker("Mode", selection: $mode) {
                        Text("Servei automàtic").tag("service")
                        Text("Tren manual").tag("manual")
                    }
                    .pickerStyle(.segmented)
                    .disabled(runningActivity != nil)

                    if mode == "manual" {
                        Picker("Tren L4", selection: $train) {
                            ForEach(401...415, id: \.self) { number in
                                Text(String(number)).tag(String(number))
                            }
                        }
                        .disabled(runningActivity != nil)
                    } else {
                        Text(serviceDate.isEmpty ? "Obre aquesta pantalla des del servei carregat." : "Horari: \(serviceDate) · \(schedule.split(separator: ";").count) trams")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Live Activity") {
                    Label(activitiesEnabled ? "Les activitats en directe estan permeses" : "Activa les activitats en directe a Configuració", systemImage: activitiesEnabled ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                        .foregroundStyle(activitiesEnabled ? .green : .orange)
                    if let activity = runningActivity {
                        Text("En curs · tren \(activity.content.state.train)")
                            .font(.headline)
                        Button("Atura la Live Activity", role: .destructive) {
                            Task { await stopActivity() }
                        }
                        .disabled(isStarting)
                    } else {
                        Button {
                            Task { await startActivity() }
                        } label: {
                            if isStarting {
                                ProgressView("Iniciant…")
                            } else {
                                Label("Inicia la Live Activity", systemImage: "livephoto")
                            }
                        }
                        .disabled(isStarting || !activitiesEnabled || (mode == "service" && schedule.isEmpty))
                    }
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Prova") {
                    Text("La Live Activity mostra el tren, l’estació, la via i l’antiguitat de les dades a la pantalla bloquejada i, en models compatibles, a la Dynamic Island.")
                    Text("Les actualitzacions remotes depenen d’APNs i de la configuració del servidor. iOS en pot limitar la freqüència.")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Miralin · Live Activity")
            .onOpenURL(perform: handleDeepLink)
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .active, let activity = runningActivity {
                    beginForegroundPolling(activity)
                } else {
                    pollingTask?.cancel()
                }
            }
            .onAppear {
                if let activity = runningActivity {
                    activityID = activity.id
                    beginForegroundPolling(activity)
                    statusMessage = "Hi ha una Live Activity activa."
                }
            }
        }
    }

    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "tmbservei-ios" else { return }
        if url.host == "stop" {
            Task { await stopActivity() }
            return
        }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let values = Dictionary(items.map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { first, _ in first })
        mode = values["mode"] == "service" ? "service" : "manual"
        if let selected = values["train"], (401...415).contains(Int(selected) ?? 0) {
            train = selected
        }
        serviceDate = values["date"] ?? ""
        schedule = values["schedule"] ?? ""
        statusMessage = "Opció rebuda. Revisa el mode i prem «Inicia la Live Activity»."
    }

    @MainActor
    private func startActivity() async {
        guard activitiesEnabled else {
            statusMessage = "A l’iPhone, ves a Configuració → Miralin Tren Ull i activa les activitats en directe."
            return
        }
        if mode == "service" && schedule.isEmpty {
            statusMessage = "Torna a la miniapp i carrega el servei abans d’iniciar el seguiment automàtic."
            return
        }

        isStarting = true
        defer { isStarting = false }
        do {
            for previous in Activity<TrainActivityAttributes>.activities {
                await previous.end(nil, dismissalPolicy: .immediate)
            }

            let initialTrain = mode == "manual" ? train : scheduledTrain(at: Date()) ?? firstScheduledTrain() ?? train
            let attributes = TrainActivityAttributes(mode: mode, serviceDate: serviceDate, startedAt: Date().timeIntervalSince1970)
            let initial = TrainActivityAttributes.ContentState(
                train: initialTrain,
                station: "Connectant…",
                track: "—",
                status: "Esperant dades",
                destination: "—",
                updatedAt: Date().timeIntervalSince1970,
                stale: true
            )
            let content = ActivityContent(state: initial, staleDate: Date().addingTimeInterval(90))
            let activity = try Activity.request(attributes: attributes, content: content, pushType: .token)
            activityID = activity.id
            lastState = initial
            statusMessage = "Live Activity iniciada. Esperant el token d’actualització…"

            tokenTask?.cancel()
            tokenTask = Task {
                for await token in activity.pushTokenUpdates {
                    guard !Task.isCancelled else { break }
                    await registerToken(token, for: activity)
                }
            }
            beginForegroundPolling(activity)
        } catch {
            statusMessage = "No s’ha pogut iniciar la Live Activity: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func stopActivity() async {
        pollingTask?.cancel()
        tokenTask?.cancel()
        guard let activity = runningActivity else { return }
        await postJSON(path: "live-activity/stop", body: ["activityId": activity.id])
        await activity.end(nil, dismissalPolicy: .immediate)
        activityID = nil
        lastState = nil
        statusMessage = "Live Activity aturada."
    }

    private func beginForegroundPolling(_ activity: Activity<TrainActivityAttributes>) {
        pollingTask?.cancel()
        pollingTask = Task {
            while !Task.isCancelled {
                await refreshActivity(activity)
                try? await Task.sleep(for: .seconds(15))
            }
        }
    }

    private func refreshActivity(_ activity: Activity<TrainActivityAttributes>) async {
        let selectedTrain = activity.attributes.mode == "service" ? (scheduledTrain(at: Date()) ?? firstScheduledTrain()) : activity.content.state.train
        guard let selectedTrain else { return }
        guard let url = URL(string: "trains?ids=\(selectedTrain)", relativeTo: apiBase) else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard
                let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                let trains = root["trains"] as? [String: Any],
                let item = trains[selectedTrain] as? [String: Any]
            else { return }

            let isStale = (root["stale"] as? Bool ?? true) || !(root["connected"] as? Bool ?? false)
            let now = Date()
            let next = TrainActivityAttributes.ContentState(
                train: selectedTrain,
                station: item["station"] as? String ?? "Sense dades",
                track: String(describing: item["platformTrack"] ?? item["track"] ?? "—"),
                status: translatedStatus(item["status"] as? String),
                destination: item["destination"] as? String ?? "—",
                updatedAt: ((root["receivedAt"] as? String).flatMap { ISO8601DateFormatter().date(from: $0) } ?? now).timeIntervalSince1970,
                stale: isStale
            )
            let changed = lastState?.train != next.train ||
                lastState?.station != next.station ||
                lastState?.track != next.track ||
                lastState?.status != next.status ||
                lastState?.destination != next.destination ||
                lastState?.stale != next.stale
            if changed || now.timeIntervalSince(lastPublishedAt) >= 300 {
                await activity.update(ActivityContent(state: next, staleDate: now.addingTimeInterval(90)))
                lastState = next
                lastPublishedAt = now
            }
        } catch {
            // Es manté l’últim estat visible i el sistema el marcarà com a antic.
        }
    }

    private func registerToken(_ token: Data, for activity: Activity<TrainActivityAttributes>) async {
        let hex = token.map { String(format: "%02x", $0) }.joined()
        let environment = tokenEnvironment
        let body: [String: Any] = [
            "action": "start",
            "activityId": activity.id,
            "token": hex,
            "environment": environment,
            "mode": activity.attributes.mode,
            "train": activity.content.state.train,
            "date": activity.attributes.serviceDate,
            "schedule": schedule
        ]
        let result = await postJSON(path: "live-activity/start", body: body)
        if let result, let remote = result["remoteUpdatesConfigured"] as? Bool {
            await MainActor.run {
                statusMessage = remote
                    ? "Live Activity activa. Les actualitzacions remotes estan connectades."
                    : "Live Activity activa. La prova visual funciona; falta configurar APNs per rebre canvis amb l’app tancada."
            }
        } else {
            await MainActor.run {
                statusMessage = "Live Activity activa en aquest iPhone; no s’ha pogut registrar l’actualització remota."
            }
        }
    }

    private var tokenEnvironment: String {
        #if DEBUG
        return "sandbox"
        #else
        return "production"
        #endif
    }

    private func postJSON(path: String, body: [String: Any]) async -> [String: Any]? {
        guard let url = URL(string: path, relativeTo: apiBase) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let statusCode = (response as? HTTPURLResponse)?.statusCode,
                  (200..<300).contains(statusCode) else { return nil }
            return try JSONSerialization.jsonObject(with: data) as? [String: Any]
        } catch {
            return nil
        }
    }

    private func translatedStatus(_ status: String?) -> String {
        switch status {
        case "arriving": return "Aproximació"
        case "en_route": return "En circulació"
        case "waiting_departure": return "Esperant sortida"
        default: return status ?? "Estat desconegut"
        }
    }

    private func scheduledTrain(at now: Date) -> String? {
        guard mode == "service", !serviceDate.isEmpty else { return nil }
        let dayParts = Calendar.current.dateComponents([.year, .month, .day], from: now)
        let serviceParts = serviceDate.split(separator: "-").compactMap { Int($0) }
        guard serviceParts.count == 3,
              let serviceDay = Calendar.current.date(from: DateComponents(year: serviceParts[0], month: serviceParts[1], day: serviceParts[2])),
              let nowDay = Calendar.current.date(from: dayParts) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: serviceDay, to: nowDay).day ?? 0
        let minute = days * 1440 + Calendar.current.component(.hour, from: now) * 60 + Calendar.current.component(.minute, from: now)
        let trips = parsedSchedule()
        if let active = trips.first(where: { minute >= $0.start && minute < $0.end }) { return active.train }
        return trips.first(where: { $0.start > minute })?.train ?? trips.last?.train
    }

    private func firstScheduledTrain() -> String? { parsedSchedule().first?.train }

    private func parsedSchedule() -> [(start: Int, end: Int, train: String)] {
        schedule.split(separator: ";").compactMap { row in
            let fields = row.split(separator: ",")
            guard fields.count == 3,
                  let start = Int(fields[0]), let end = Int(fields[1]),
                  (401...415).contains(Int(fields[2]) ?? 0), end > start else { return nil }
            return (start, end, String(fields[2]))
        }
    }
}
