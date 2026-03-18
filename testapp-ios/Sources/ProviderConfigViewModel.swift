import Foundation
import TrackFlowCore
import trackflow_provider_firebase_ios
import trackflow_provider_amplitude_ios
import trackflow_provider_mixpanel_ios

struct ProviderConfig: Identifiable {
    let id: String // provider key
    let name: String
    var enabled: Bool
    var apiKey: String
    var secondaryKey: String? // e.g. datasetId for Adobe Edge
    let requiresApiKey: Bool
    let requiresSecondaryKey: Bool
    let secondaryKeyLabel: String?
}

class ProviderConfigViewModel: ObservableObject {
    @Published var providers: [ProviderConfig] = []
    @Published var logLevel: String = "debug"
    @Published var batchSize: Int = 10
    @Published var flushIntervalSec: Int = 15
    @Published var lastInitTime: Date?

    private let defaults = UserDefaults.standard

    init() {
        loadProviders()
    }

    private func loadProviders() {
        providers = [
            ProviderConfig(
                id: "firebase", name: "Firebase",
                enabled: defaults.bool(forKey: "provider_firebase_enabled"),
                apiKey: "", requiresApiKey: false,
                requiresSecondaryKey: false, secondaryKeyLabel: nil
            ),
            ProviderConfig(
                id: "amplitude", name: "Amplitude",
                enabled: defaults.object(forKey: "provider_amplitude_enabled") as? Bool ?? true,
                apiKey: defaults.string(forKey: "provider_amplitude_key") ?? bundleValue("AMPLITUDE_API_KEY"),
                requiresApiKey: true,
                requiresSecondaryKey: false, secondaryKeyLabel: nil
            ),
            ProviderConfig(
                id: "mixpanel", name: "Mixpanel",
                enabled: defaults.bool(forKey: "provider_mixpanel_enabled"),
                apiKey: defaults.string(forKey: "provider_mixpanel_key") ?? bundleValue("MIXPANEL_TOKEN"),
                requiresApiKey: true,
                requiresSecondaryKey: false, secondaryKeyLabel: nil
            ),
        ]
    }

    func save() {
        for p in providers {
            defaults.set(p.enabled, forKey: "provider_\(p.id)_enabled")
            if p.requiresApiKey {
                defaults.set(p.apiKey, forKey: "provider_\(p.id)_key")
            }
            if p.requiresSecondaryKey {
                defaults.set(p.secondaryKey, forKey: "provider_\(p.id)_dataset")
            }
        }
    }

    // MARK: - Per-provider key mappings
    //
    // Each provider can remap generic property keys to provider-specific names.
    // This lets you track with one consistent API while each provider receives
    // the keys it expects. Unmapped keys pass through unchanged.
    //
    // Example: track("product_viewed", ["product_id": "SKU-123", "price": 29.99])
    //   → Adobe receives:     { "eVar5": "SKU-123", "eVar12": 29.99 }
    //   → Amplitude receives:  { "Product ID": "SKU-123", "Revenue": 29.99 }
    //   → Firebase receives:   { "item_id": "SKU-123", "value": 29.99 }
    //   → Mixpanel receives:   { "$product_id": "SKU-123", "$amount": 29.99 }

    private let firebaseKeyMap: [String: String] = [
        "product_id": "item_id",
        "product_name": "item_name",
        "category": "item_category",
        "price": "value",
        "quantity": "quantity",
        "currency": "currency",
        "search_query": "search_term",
    ]

    private let amplitudeKeyMap: [String: String] = [
        "product_id": "Product ID",
        "product_name": "Product Name",
        "category": "Product Category",
        "price": "Revenue",
        "quantity": "Quantity",
        "search_query": "Search Query",
        "search_results": "Search Results Count",
    ]

    private let mixpanelKeyMap: [String: String] = [
        "product_id": "$product_id",
        "product_name": "$product_name",
        "price": "$amount",
        "currency": "$currency",
        "screen": "mp_page",
        "screen_name": "mp_page",
    ]

    func reinitialize() {
        save()

        for p in providers where p.enabled {
            switch p.id {
            case "firebase":
                FirebaseIosProviderKt.registerFirebaseProvider(
                    keyMap: firebaseKeyMap
                )
            case "amplitude":
                guard !p.apiKey.isEmpty else { continue }
                AmplitudeIosProviderKt.registerAmplitudeProvider(
                    apiKey: p.apiKey,
                    keyMap: amplitudeKeyMap
                )
            case "mixpanel":
                guard !p.apiKey.isEmpty else { continue }
                MixpanelIosProviderKt.registerMixpanelProvider(
                    token: p.apiKey,
                    keyMap: mixpanelKeyMap
                )
            default:
                break
            }
        }

        let level: LogLevel
        switch logLevel {
        case "verbose": level = .verbose
        case "debug": level = .debug
        case "info": level = .info
        case "warn": level = .warn
        case "error": level = .error
        default: level = .debug
        }

        TrackFlowIos.shared.initialize(
            logLevel: level,
            batchSize: Int32(batchSize),
            flushIntervalMs: Int64(flushIntervalSec) * 1000,
            licenseKey: nil
        )

        lastInitTime = Date()
    }

    private func bundleValue(_ key: String) -> String {
        Bundle.main.infoDictionary?[key] as? String ?? ""
    }
}
