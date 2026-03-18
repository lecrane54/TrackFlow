import SwiftUI
import TrackFlowCore

struct ContentView: View {
    @State private var logMessages: [String] = []

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                Text("TrackFlow iOS Test")
                    .font(.title)
                    .padding(.top)

                Group {
                    Button("Track Event") {
                        trackEvent()
                    }

                    Button("Track State") {
                        trackState()
                    }

                    Button("Identify User") {
                        identifyUser()
                    }

                    Button("Reset Identity") {
                        resetIdentity()
                    }
                }
                .buttonStyle(.borderedProminent)

                Divider()

                ScrollView {
                    LazyVStack(alignment: .leading) {
                        ForEach(logMessages.reversed(), id: \.self) { msg in
                            Text(msg)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .frame(maxHeight: .infinity)
            }
            .padding()
            .navigationTitle("TrackFlow")
        }
    }

    private func trackEvent() {
        // Map overload uses properties_: (trailing underscore) in Swift
        TrackFlow.shared.track(name: "test_purchase", properties_: ["item": "sword", "price": "9.99"])
        log("Tracked: test_purchase")
    }

    private func trackState() {
        TrackFlow.shared.trackState(name: "home_screen", properties_: ["source": "deeplink"])
        log("Tracked state: home_screen")
    }

    private func identifyUser() {
        TrackFlow.shared.identify(userId: "user_123", traits_: ["plan": "premium", "signup_date": "2026-03-18"])
        log("Identified: user_123")
    }

    private func resetIdentity() {
        TrackFlow.shared.resetIdentity()
        log("Identity reset")
    }

    private func log(_ message: String) {
        logMessages.append("[\(Date().formatted(.dateTime.hour().minute().second()))] \(message)")
    }
}

#Preview {
    ContentView()
}
