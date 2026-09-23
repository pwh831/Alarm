import FitWakeCore
import Foundation

/// 지금 울리고 있는(미션을 기다리는) 알람. 앱이 꺼져도 유지되도록 UserDefaults에 저장한다.
struct ActiveRing: Codable, Identifiable, Equatable {
    var alarmID: UUID
    var label: String
    var exercise: Exercise
    var difficulty: Difficulty
    var targetReps: Int
    var startedAt: Date
    var backupID: UUID?

    var id: UUID { alarmID }
}

/// 알람 → 미션 → 해제 흐름을 관리한다. 앱 화면과 알람 버튼(App Intent) 양쪽에서 호출된다.
enum RingCoordinator {
    /// "중단"을 누른 뒤 다시 울리기까지.
    static let stopRetrySeconds: TimeInterval = 60
    /// 미션 화면에 있는 동안 백업 알람을 이만큼 뒤로 계속 미룬다.
    static let missionHeartbeatSeconds: TimeInterval = 180

    private static let key = "activeRing"

    static var active: ActiveRing? {
        get {
            guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
            return try? JSONDecoder().decode(ActiveRing.self, from: data)
        }
        set {
            if let newValue, let data = try? JSONEncoder().encode(newValue) {
                UserDefaults.standard.set(data, forKey: key)
            } else {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }
    }

    /// 알람이 울려서 사용자가 버튼을 눌렀을 때. 이미 진행 중이면 그대로 둔다.
    @discardableResult
    static func begin(alarmID: UUID) -> ActiveRing {
        if let ring = active, ring.alarmID == alarmID { return ring }
        var alarms = Storage.alarms
        let alarm = alarms.first { $0.id == alarmID } ?? WakeAlarm(id: alarmID)
        // 1회용 알람은 울린 뒤 꺼진 상태로 표시한다.
        if !alarm.isRepeating, let i = alarms.firstIndex(where: { $0.id == alarmID }) {
            alarms[i].enabled = false
            Storage.alarms = alarms
        }
        let ring = ActiveRing(
            alarmID: alarmID,
            label: alarm.label,
            exercise: alarm.pickExercise(),
            difficulty: alarm.difficulty,
            targetReps: alarm.targetReps,
            startedAt: Date()
        )
        active = ring
        return ring
    }

    /// 이전 백업 알람을 지우고 [seconds] 뒤에 새로 예약한다.
    static func scheduleBackup(after seconds: TimeInterval) async {
        guard var ring = active else { return }
        if let old = ring.backupID { AlarmScheduler.cancel(old) }
        let id = UUID()
        do {
            try await AlarmScheduler.scheduleBackup(id: id, alarmID: ring.alarmID, at: Date().addingTimeInterval(seconds))
            ring.backupID = id
        } catch {
            ring.backupID = nil
        }
        active = ring
    }

    /// 미션 완료 또는 긴급 해제. 백업 알람을 지우고 기록을 남긴다.
    static func finish(method: DismissMethod, reps: Int) {
        guard let ring = active else { return }
        if let backup = ring.backupID { AlarmScheduler.cancel(backup) }
        var records = Storage.records
        records.append(
            WakeRecord(
                alarmID: ring.alarmID,
                label: ring.label,
                ringStartedAt: ring.startedAt,
                dismissedAt: Date(),
                method: method,
                exercise: method == .mission ? ring.exercise : nil,
                reps: reps
            )
        )
        Storage.records = records
        active = nil
    }

    @MainActor
    static func notifyChanged() {
        NotificationCenter.default.post(name: .fitWakeRingChanged, object: nil)
    }
}
