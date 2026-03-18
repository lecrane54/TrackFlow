import SwiftUI
import TrackFlowCore
import trackflow_provider_firebase_ios
import trackflow_provider_amplitude_ios
import trackflow_provider_mixpanel_ios

@main
struct TrackFlowTestApp: App {
    init() {
        setupTrackFlow()
    }

    var body: some Scene {
        WindowGroup {
            DebugTabView()
        }
    }

    private func setupTrackFlow() {
        let amplitudeKey = Bundle.main.infoDictionary?["AMPLITUDE_API_KEY"] as? String ?? ""
        let mixpanelToken = Bundle.main.infoDictionary?["MIXPANEL_TOKEN"] as? String ?? ""
        // Per-provider key maps: same event properties get renamed to match each provider's conventions
        // e.g. track("product_viewed", ["product_id": "SKU-123", "price": "29.99"])
        //   → Amplitude receives: { "Product ID": "SKU-123", "Revenue": "29.99" }

        let amplitudeKeyMap: [String: String] = [
            "product_id": "Product ID", "product_name": "Product Name",
            "category": "Product Category", "price": "Revenue",
            "quantity": "Quantity", "search_query": "Search Query",
            "search_results": "Search Results Count",
        ]

        // Register providers with key remapping
        //FirebaseIosProviderKt.registerFirebaseProvider(keyMap: firebaseKeyMap)
        AmplitudeIosProviderKt.registerAmplitudeProvider(
            apiKey: amplitudeKey, keyMap: amplitudeKeyMap
        )
        //MixpanelIosProviderKt.registerMixpanelProvider(token: mixpanelToken, keyMap: mixpanelKeyMap)

        // Initialize TrackFlow with all registered providers
        TrackFlowIos.shared.initialize(logLevel: .debug, batchSize: 10, flushIntervalMs: 15_000, licenseKey: nil)

        print("TrackFlow initialized with per-provider key remapping")
    }
}
