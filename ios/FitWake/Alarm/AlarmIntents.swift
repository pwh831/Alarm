import AppIntents
import Foundation

/// 알람 화면의 "중단" 버튼. 알람은 멈추지만 미션을 끝내지 않았으니 1분 뒤 다시 울린다.
struct StopAlarmIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "알람 중단"
    static let description = IntentDescription("알람을 잠시 멈춥니다. 미션을 끝내지 않으면 1분 뒤 다시 울려요.")

    @Parameter(title: "알람 ID")
    var alarmID: String

    init() {}

    init(alarmID: String) {
        self.alarmID = alarmID
    }

    func perform() async throws -> some IntentResult {
        guard let id = UUID(uuidString: alarmID) else { return .result() }
        RingCoordinator.begin(alarmID: id)
        await RingCoordinator.scheduleBackup(after: RingCoordinator.stopRetrySeconds)
        await RingCoordinator.notifyChanged()
        return .result()
    }
}

/// 알람 화면의 "미션 시작" 버튼. 앱을 열어 미션 화면을 띄운다.
struct OpenMissionIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "미션 시작"
    static let description = IntentDescription("FitWake를 열어 운동 미션을 시작합니다.")
    static let openAppWhenRun = true

    @Parameter(title: "알람 ID")
    var alarmID: String

    init() {}

    init(alarmID: String) {
        self.alarmID = alarmID
    }

    func perform() async throws -> some IntentResult {
        guard let id = UUID(uuidString: alarmID) else { return .result() }
        RingCoordinator.begin(alarmID: id)
        AlarmScheduler.stopAlerting()
        // 미션 도중 앱을 나가 버리면 이 백업이 다시 울린다. 미션 화면이 주기적으로 뒤로 미룬다.
        await RingCoordinator.scheduleBackup(after: RingCoordinator.missionHeartbeatSeconds)
        await RingCoordinator.notifyChanged()
        return .result()
    }
}
