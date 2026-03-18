import SwiftUI
import TrackFlowCore

struct EventTestingView: View {
    @State private var logMessages: [String] = []
    @State private var customEventName = ""
    @State private var customProperties: [(key: String, value: String)] = []
    @State private var batchCount = 5
    @State private var userId = "user_123"

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    templateSection
                    customEventSection
                    identitySection
                    batchSection
                    logSection
                }
                .padding()
            }
            .navigationTitle("Event Testing")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    // MARK: - Templates

    private var templateSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Quick Templates")

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                // Purchase — key replacement per provider:
                // Amplitude: product_id → Product ID, price → Revenue
                // Firebase:  product_id → item_id, price → value
                // Mixpanel:  product_id → $product_id, price → $amount
                TemplateButton(title: "Purchase", icon: "cart.fill", color: .green) {
                    TrackFlow.shared.track(name: "purchase", properties_: [
                        "product_id": "SKU-9876",
                        "product_name": "Running Shoes",
                        "category": "footwear",
                        "price": "129.99",
                        "quantity": "1",
                        "currency": "USD"
                    ])
                    log("purchase")
                }

                // Screen view — remapped keys:
                // Mixpanel:  screen → mp_page
                TemplateButton(title: "Screen View", icon: "rectangle.fill", color: .blue) {
                    TrackFlow.shared.trackState(name: "product_detail", properties_: [
                        "screen": "ProductDetailVC",
                        "screen_name": "Product Detail",
                        "source": "search",
                        "category": "footwear"
                    ])
                    log("screen: product_detail")
                }

                TemplateButton(title: "Sign Up", icon: "person.badge.plus", color: .purple) {
                    TrackFlow.shared.track(name: "sign_up", properties_: [
                        "method": "email", "referral": "campaign_42"
                    ])
                    log("sign_up")
                }

                // Add to Cart — exercises product key mappings across all providers
                TemplateButton(title: "Add to Cart", icon: "plus.circle.fill", color: .orange) {
                    TrackFlow.shared.track(name: "add_to_cart", properties_: [
                        "product_id": "SKU-1234",
                        "product_name": "Trail Jacket",
                        "category": "outerwear",
                        "quantity": "2",
                        "price": "89.99",
                        "currency": "USD"
                    ])
                    log("add_to_cart")
                }

                // Search — key replacement for search properties:
                // Amplitude:  search_query → Search Query, search_results → Search Results Count
                // Firebase:   search_query → search_term
                TemplateButton(title: "Search", icon: "magnifyingglass", color: .teal) {
                    TrackFlow.shared.track(name: "search_performed", properties_: [
                        "search_query": "running shoes",
                        "search_results": "42",
                        "screen": "search_results",
                        "filter_applied": "brand:nike"
                    ])
                    log("search_performed")
                }

                // Error — shows error property mapping:
                // Keys pass through unchanged to most providers
                TemplateButton(title: "Error", icon: "exclamationmark.triangle.fill", color: .red) {
                    TrackFlow.shared.track(name: "app_error", properties_: [
                        "error_type": "network",
                        "error_code": "503",
                        "screen": "checkout",
                        "message": "Service unavailable"
                    ])
                    log("app_error")
                }
            }
        }
    }

    // MARK: - Custom Event

    private var customEventSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Custom Event")

            TextField("Event name", text: $customEventName)
                .textFieldStyle(.roundedBorder)

            ForEach(customProperties.indices, id: \.self) { i in
                HStack {
                    TextField("Key", text: Binding(
                        get: { customProperties[i].key },
                        set: { customProperties[i] = (key: $0, value: customProperties[i].value) }
                    ))
                    .textFieldStyle(.roundedBorder)

                    TextField("Value", text: Binding(
                        get: { customProperties[i].value },
                        set: { customProperties[i] = (key: customProperties[i].key, value: $0) }
                    ))
                    .textFieldStyle(.roundedBorder)

                    Button { customProperties.remove(at: i) } label: {
                        Image(systemName: "minus.circle.fill")
                            .foregroundColor(.red)
                    }
                }
            }

            HStack {
                Button {
                    customProperties.append((key: "", value: ""))
                } label: {
                    Label("Add Property", systemImage: "plus.circle")
                        .font(.caption)
                }

                Spacer()

                Button("Fire Event") {
                    guard !customEventName.isEmpty else { return }
                    var props: [String: Any] = [:]
                    for p in customProperties where !p.key.isEmpty {
                        props[p.key] = p.value
                    }
                    if props.isEmpty {
                        TrackFlow.shared.track(name: customEventName)
                    } else {
                        TrackFlow.shared.track(name: customEventName, properties_: props)
                    }
                    log(customEventName)
                }
                .buttonStyle(.borderedProminent)
                .disabled(customEventName.isEmpty)
            }
        }
    }

    // MARK: - Identity

    private var identitySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Identity")

            HStack {
                TextField("User ID", text: $userId)
                    .textFieldStyle(.roundedBorder)

                Button("Identify") {
                    TrackFlow.shared.identify(userId: userId, traits_: [
                        "plan": "premium", "signup_date": "2026-03-18"
                    ])
                    log("identify: \(userId)")
                }
                .buttonStyle(.borderedProminent)
            }

            Button("Reset Identity") {
                TrackFlow.shared.resetIdentity()
                log("identity reset")
            }
            .buttonStyle(.bordered)
            .tint(.red)
        }
    }

    // MARK: - Batch

    private var batchSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Batch Test")

            HStack {
                Text("Count: \(batchCount)")
                Slider(value: Binding(
                    get: { Double(batchCount) },
                    set: { batchCount = Int($0) }
                ), in: 1...50, step: 1)
            }

            Button("Fire Batch") {
                for i in 1...batchCount {
                    TrackFlow.shared.track(name: "batch_event_\(i)", properties_: [
                        "index": "\(i)", "total": "\(batchCount)"
                    ])
                }
                log("batch: \(batchCount) events")
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)
        }
    }

    // MARK: - Log

    private var logSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            SectionHeader(title: "Log")

            if logMessages.isEmpty {
                Text("No events fired yet")
                    .foregroundColor(.secondary)
                    .font(.caption)
            } else {
                ForEach(logMessages.reversed(), id: \.self) { msg in
                    Text(msg)
                        .font(.caption2.monospaced())
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    private func log(_ event: String) {
        let time = Date().formatted(.dateTime.hour().minute().second())
        logMessages.append("[\(time)] \(event)")
    }
}

// MARK: - Helpers

struct SectionHeader: View {
    let title: String
    var body: some View {
        Text(title)
            .font(.headline)
            .padding(.top, 4)
    }
}

struct TemplateButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.title2)
                Text(title)
                    .font(.caption.bold())
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(color.opacity(0.12))
            .foregroundColor(color)
            .cornerRadius(10)
        }
    }
}
