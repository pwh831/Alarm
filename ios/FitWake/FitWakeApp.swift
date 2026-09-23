import FitWakeCore
import SwiftUI

@main
struct FitWakeApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(.orange)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { model.reload() }
                }
                .onReceive(NotificationCenter.default.publisher(for: .fitWakeRingChanged)) { _ in
                    model.reload()
                }
                .task { await model.resyncAlarms() }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if model.onboardingDone {
                HomeView()
            } else {
                OnboardingView()
            }
        }
        // 울리는 중인 알람이 있으면 미션을 끝내기 전까지 빠져나갈 수 없다.
        .fullScreenCover(item: $model.activeRing) { ring in
            RingMissionView(ring: ring)
                .interactiveDismissDisabled()
        }
    }
}
