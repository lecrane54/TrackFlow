import Foundation
import TrackFlowCore
import Combine

class EventMonitorViewModel: ObservableObject {
    @Published var records: [DeliveryRecord] = []
    @Published var searchQuery: String = ""
    @Published var statusFilter: DeliveryStatus? = nil
    @Published var providerFilter: String? = nil

    private var timer: Timer?

    var filteredRecords: [DeliveryRecord] {
        records.filter { record in
            if let sf = statusFilter, record.status != sf { return false }
            if let pf = providerFilter, record.providerKey != pf { return false }
            if !searchQuery.isEmpty {
                let q = searchQuery.lowercased()
                let nameMatch = record.payload.eventName.lowercased().contains(q)
                let providerMatch = record.providerKey.lowercased().contains(q)
                let mappedMatch = record.mappedEvent?.name.lowercased().contains(q) ?? false
                if !nameMatch && !providerMatch && !mappedMatch { return false }
            }
            return true
        }
    }

    var providerKeys: [String] {
        Array(Set(records.map { $0.providerKey })).sorted()
    }

    var deliveredCount: Int { records.filter { $0.status == .delivered }.count }
    var failedCount: Int { records.filter { $0.status == .failed }.count }

    init() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            DispatchQueue.main.async {
                self?.poll()
            }
        }
    }

    private func poll() {
        let monitor = TrackFlow.shared.eventMonitor()
        if let list = monitor.records.value as? [DeliveryRecord] {
            if list.count != records.count {
                records = list
            }
        }
    }

    func clear() {
        TrackFlow.shared.eventMonitor().clear()
        records = []
    }

    func exportJson() -> String {
        TrackFlow.shared.eventMonitor().exportAsJson()
    }

    deinit {
        timer?.invalidate()
    }
}
