import SwiftUI
import TrackFlowCore

struct EventMonitorView: View {
    @StateObject private var viewModel = EventMonitorViewModel()
    @State private var showShareSheet = false
    @State private var jsonExport = ""

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                statsBar
                searchBar
                filterChips
                eventList
                bottomBar
            }
            .navigationTitle("Event Monitor")
            .navigationBarTitleDisplayMode(.inline)
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: [jsonExport])
        }
    }

    private var statsBar: some View {
        HStack {
            Label("\(viewModel.deliveredCount)", systemImage: "checkmark.circle.fill")
                .foregroundColor(.green)
            Spacer()
            Label("\(viewModel.failedCount)", systemImage: "xmark.circle.fill")
                .foregroundColor(.red)
            Spacer()
            Text("\(viewModel.filteredRecords.count) of \(viewModel.records.count) events")
                .foregroundColor(.secondary)
                .font(.caption)
        }
        .font(.subheadline.bold())
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(Color(.systemGroupedBackground))
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            TextField("Search events...", text: $viewModel.searchQuery)
                .textFieldStyle(.plain)
            if !viewModel.searchQuery.isEmpty {
                Button { viewModel.searchQuery = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(8)
        .background(Color(.systemBackground))
        .cornerRadius(8)
        .padding(.horizontal)
        .padding(.vertical, 4)
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(label: "All", isSelected: viewModel.statusFilter == nil) {
                    viewModel.statusFilter = nil
                }
                FilterChip(label: "Delivered", color: .green,
                           isSelected: viewModel.statusFilter == .delivered) {
                    viewModel.statusFilter = .delivered
                }
                FilterChip(label: "Failed", color: .red,
                           isSelected: viewModel.statusFilter == .failed) {
                    viewModel.statusFilter = .failed
                }
                FilterChip(label: "Offline", color: .orange,
                           isSelected: viewModel.statusFilter == .queuedOffline) {
                    viewModel.statusFilter = .queuedOffline
                }
                FilterChip(label: "Dropped", color: .gray,
                           isSelected: viewModel.statusFilter == .droppedByMiddleware) {
                    viewModel.statusFilter = .droppedByMiddleware
                }

                Divider().frame(height: 20)

                FilterChip(label: "All Providers", isSelected: viewModel.providerFilter == nil) {
                    viewModel.providerFilter = nil
                }
                ForEach(viewModel.providerKeys, id: \.self) { key in
                    FilterChip(label: key, color: .purple,
                               isSelected: viewModel.providerFilter == key) {
                        viewModel.providerFilter = key
                    }
                }
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 4)
    }

    private var eventList: some View {
        List {
            if viewModel.filteredRecords.isEmpty {
                Text("No events yet. Fire some from the Test tab.")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(Array(viewModel.filteredRecords.enumerated()), id: \.offset) { _, record in
                    DeliveryRecordRow(record: record)
                }
            }
        }
        .listStyle(.plain)
    }

    private var bottomBar: some View {
        HStack {
            Button {
                jsonExport = viewModel.exportJson()
                showShareSheet = true
            } label: {
                Label("Share", systemImage: "square.and.arrow.up")
            }

            Spacer()

            Button {
                UIPasteboard.general.string = viewModel.exportJson()
            } label: {
                Label("Copy JSON", systemImage: "doc.on.doc")
            }

            Spacer()

            Button(role: .destructive) {
                viewModel.clear()
            } label: {
                Label("Clear", systemImage: "trash")
            }
        }
        .font(.caption)
        .padding()
        .background(Color(.systemGroupedBackground))
    }
}

// MARK: - Delivery Record Row

struct DeliveryRecordRow: View {
    let record: DeliveryRecord
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Circle()
                    .fill(statusColor)
                    .frame(width: 10, height: 10)
                Text(record.payload.eventName)
                    .font(.subheadline.bold())
                Spacer()
                Text(formatTime(record.timestampMs))
                    .font(.caption2.monospacedDigit())
                    .foregroundColor(.secondary)
            }

            HStack(spacing: 6) {
                TagPill(text: record.providerKey, color: .purple)
                TagPill(text: statusLabel, color: statusColor)
                TagPill(text: record.payload.type.name, color: .blue)
                if let mapped = record.mappedEvent, mapped.name != record.payload.eventName {
                    TagPill(text: mapped.name, color: .teal)
                }
            }

            if expanded {
                if let props = record.payload.properties as? [String: Any], !props.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Properties").font(.caption.bold()).foregroundColor(.secondary)
                        ForEach(Array(props.keys.sorted()), id: \.self) { key in
                            Text("\(key): \(String(describing: props[key] ?? "nil"))")
                                .font(.caption2.monospaced())
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.top, 4)
                }

                if let mapped = record.mappedEvent,
                   let mappedProps = mapped.properties as? [String: Any], !mappedProps.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Mapped Properties").font(.caption.bold()).foregroundColor(.secondary)
                        ForEach(Array(mappedProps.keys.sorted()), id: \.self) { key in
                            Text("\(key): \(String(describing: mappedProps[key] ?? "nil"))")
                                .font(.caption2.monospaced())
                                .foregroundColor(.secondary)
                        }
                    }
                }

                if let error = record.error {
                    Text(error)
                        .font(.caption2)
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.red.opacity(0.8))
                        .cornerRadius(4)
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { expanded.toggle() }
    }

    private var statusColor: Color {
        switch record.status {
        case .delivered: return .green
        case .failed: return .red
        case .queuedOffline: return .orange
        case .droppedByMiddleware: return .gray
        default: return .gray
        }
    }

    private var statusLabel: String {
        switch record.status {
        case .delivered: return "Delivered"
        case .failed: return "Failed"
        case .queuedOffline: return "Offline"
        case .droppedByMiddleware: return "Dropped"
        default: return "Unknown"
        }
    }

    private func formatTime(_ ms: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(ms) / 1000.0)
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm:ss.SSS"
        return fmt.string(from: date)
    }
}

// MARK: - Helpers

struct FilterChip: View {
    let label: String
    var color: Color = .accentColor
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(isSelected ? color.opacity(0.2) : Color(.systemGray6))
                .foregroundColor(isSelected ? color : .primary)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(isSelected ? color : Color.clear, lineWidth: 1)
                )
        }
    }
}

struct TagPill: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .medium))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(4)
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
