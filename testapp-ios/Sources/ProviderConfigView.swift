import SwiftUI

struct ProviderConfigView: View {
    @StateObject private var viewModel = ProviderConfigViewModel()
    @State private var showReinitAlert = false

    var body: some View {
        NavigationView {
            Form {
                sdkSettingsSection
                providersSection
                actionsSection
            }
            .navigationTitle("Provider Config")
            .navigationBarTitleDisplayMode(.inline)
            .alert("Re-initialize TrackFlow?", isPresented: $showReinitAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Re-initialize") {
                    viewModel.reinitialize()
                }
            } message: {
                Text("This will re-register all enabled providers and re-initialize the SDK with current settings.")
            }
        }
    }

    private var sdkSettingsSection: some View {
        Section("SDK Settings") {
            Picker("Log Level", selection: $viewModel.logLevel) {
                Text("Verbose").tag("verbose")
                Text("Debug").tag("debug")
                Text("Info").tag("info")
                Text("Warn").tag("warn")
                Text("Error").tag("error")
            }

            Stepper("Batch Size: \(viewModel.batchSize)", value: $viewModel.batchSize, in: 1...100)

            Stepper("Flush Interval: \(viewModel.flushIntervalSec)s", value: $viewModel.flushIntervalSec, in: 1...300)

            if let time = viewModel.lastInitTime {
                HStack {
                    Text("Last Init")
                    Spacer()
                    Text(time.formatted(.dateTime.hour().minute().second()))
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    private var providersSection: some View {
        Section("Providers") {
            ForEach($viewModel.providers) { $provider in
                DisclosureGroup {
                    if provider.requiresApiKey {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("API Key").font(.caption).foregroundColor(.secondary)
                            TextField("Enter API key", text: $provider.apiKey)
                                .textFieldStyle(.roundedBorder)
                                .font(.system(.caption, design: .monospaced))
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                        }
                    }
                    if provider.requiresSecondaryKey, let label = provider.secondaryKeyLabel {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(label).font(.caption).foregroundColor(.secondary)
                            TextField("Enter \(label.lowercased())", text: Binding(
                                get: { provider.secondaryKey ?? "" },
                                set: { provider.secondaryKey = $0 }
                            ))
                            .textFieldStyle(.roundedBorder)
                            .font(.system(.caption, design: .monospaced))
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        }
                    }
                } label: {
                    Toggle(provider.name, isOn: $provider.enabled)
                }
            }
        }
    }

    private var actionsSection: some View {
        Section {
            Button("Save & Re-initialize") {
                showReinitAlert = true
            }
            .frame(maxWidth: .infinity)
            .foregroundColor(.white)
            .listRowBackground(Color.accentColor)
        }
    }
}
