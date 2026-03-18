import SwiftUI

struct DebugTabView: View {
    var body: some View {
        TabView {
            EventTestingView()
                .tabItem {
                    Label("Test", systemImage: "play.circle.fill")
                }

            EventMonitorView()
                .tabItem {
                    Label("Monitor", systemImage: "waveform")
                }

            ProviderConfigView()
                .tabItem {
                    Label("Config", systemImage: "gearshape.fill")
                }
        }
    }
}
