import AlarmKit
import FitWakeCore
import SwiftUI

struct FitWakeAlarmMetadata: AlarmMetadata {
    var alarmID: UUID
}

/// AlarmKit 예약. 잠금 화면에 시스템 알람으로 뜨고, 무음 모드에서도 울린다.
///
/// iOS 알람 화면에는 "중단" 버튼을 없앨 수 없다. 대신 중단을 누르면 곧 다시 울리도록
/// 백업 알람을 예약하고(StopAlarmIntent), 미션을 마쳐야만 백업이 취소된다.
enum AlarmScheduler {
    static var isAuthorized: Bool {
        AlarmManager.shared.authorizationState == .authorized
    }

    @discardableResult
    static func requestAuthorization() async -> Bool {
        switch AlarmManager.shared.authorizationState {
        case .authorized: return true
        case .denied: return false
        default: return (try? await AlarmManager.shared.requestAuthorization()) == .authorized
        }
    }

    static func schedule(_ alarm: WakeAlarm) async throws {
        cancel(alarm.id)
        guard alarm.enabled else { return }
        let time = Alarm.Schedule.Relative.Time(hour: alarm.hour, minute: alarm.minute)
        let recurrence: Alarm.Schedule.Relative.Recurrence = alarm.isRepeating
            ? .weekly(alarm.repeatDays.sorted().map(\.localeWeekday))
            : .never
        let schedule = Alarm.Schedule.relative(.init(time: time, repeats: recurrence))
        let title = alarm.label.isEmpty ? "일어날 시간이에요" : alarm.label
        _ = try await AlarmManager.shared.schedule(
            id: alarm.id,
            configuration: configuration(alarmID: alarm.id, title: title, schedule: schedule)
        )
    }

    /// 미션을 마치지 않았을 때 다시 울릴 1회용 알람.
    static func scheduleBackup(id: UUID, alarmID: UUID, at date: Date) async throws {
        _ = try await AlarmManager.shared.schedule(
            id: id,
            configuration: configuration(alarmID: alarmID, title: "미션을 끝내야 알람이 꺼져요", schedule: .fixed(date))
        )
    }

    static func cancel(_ id: UUID) {
        try? AlarmManager.shared.cancel(id: id)
    }

    /// 지금 울리고 있는 알람을 멈춘다 (미션 화면으로 들어갈 때).
    static func stopAlerting() {
        guard let alarms = try? AlarmManager.shared.alarms else { return }
        for alarm in alarms where alarm.state == .alerting {
            try? AlarmManager.shared.stop(id: alarm.id)
        }
    }

    private static func configuration(
        alarmID: UUID,
        title: String,
        schedule: Alarm.Schedule
    ) -> AlarmManager.AlarmConfiguration<FitWakeAlarmMetadata> {
        let stopButton = AlarmButton(text: "중단", textColor: .white, systemImageName: "stop.circle")
        let missionButton = AlarmButton(text: "미션 시작", textColor: .black, systemImageName: "figure.strengthtraining.traditional")
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: stopButton,
            secondaryButton: missionButton,
            secondaryButtonBehavior: .custom
        )
        let attributes = AlarmAttributes(
            presentation: AlarmPresentation(alert: alert),
            metadata: FitWakeAlarmMetadata(alarmID: alarmID),
            tintColor: .orange
        )
        return AlarmManager.AlarmConfiguration(
            schedule: schedule,
            attributes: attributes,
            stopIntent: StopAlarmIntent(alarmID: alarmID.uuidString),
            secondaryIntent: OpenMissionIntent(alarmID: alarmID.uuidString)
        )
    }
}

extension Weekday {
    var localeWeekday: Locale.Weekday {
        switch self {
        case .monday: .monday
        case .tuesday: .tuesday
        case .wednesday: .wednesday
        case .thursday: .thursday
        case .friday: .friday
        case .saturday: .saturday
        case .sunday: .sunday
        }
    }
}
