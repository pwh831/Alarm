import FitWakeCore
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published var alarms: [WakeAlarm] = []
    @Published var records: [WakeRecord] = []
    @Published var activeRing: ActiveRing?
    @Published var onboardingDone = UserDefaults.standard.bool(forKey: "onboardingDone")
    @Published var errorMessage: String?

    init() {
        reload()
    }

    func reload() {
        alarms = Storage.alarms.sorted { ($0.hour, $0.minute) < ($1.hour, $1.minute) }
        records = Storage.records.sorted { $0.ringStartedAt > $1.ringStartedAt }
        activeRing = RingCoordinator.active
    }

    func finishOnboarding() {
        UserDefaults.standard.set(true, forKey: "onboardingDone")
        onboardingDone = true
    }

    func save(_ alarm: WakeAlarm) async {
        var list = Storage.alarms
        if let i = list.firstIndex(where: { $0.id == alarm.id }) {
            list[i] = alarm
        } else {
            list.append(alarm)
        }
        Storage.alarms = list
        reload()
        do {
            try await AlarmScheduler.schedule(alarm)
        } catch {
            errorMessage = "알람을 예약하지 못했어요. 설정 앱에서 FitWake의 알람 권한을 확인해 주세요.\n(\(error.localizedDescription))"
        }
    }

    func setEnabled(_ alarm: WakeAlarm, _ enabled: Bool) async {
        var copy = alarm
        copy.enabled = enabled
        await save(copy)
    }

    func delete(_ alarm: WakeAlarm) {
        AlarmScheduler.cancel(alarm.id)
        Storage.alarms = Storage.alarms.filter { $0.id != alarm.id }
        reload()
    }

    /// 앱을 켤 때마다 저장된 알람과 시스템 예약을 맞춘다.
    func resyncAlarms() async {
        guard AlarmScheduler.isAuthorized else { return }
        for alarm in Storage.alarms {
            try? await AlarmScheduler.schedule(alarm)
        }
    }
}
