import Foundation

public enum DismissMethod: String, Codable, Sendable {
    case mission = "MISSION"
    case emergency = "EMERGENCY"
}

/// 알람 한 번의 기상 기록 (PRD ST-01, ST-02).
public struct WakeRecord: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var alarmID: UUID
    public var label: String
    public var ringStartedAt: Date
    public var dismissedAt: Date
    public var method: DismissMethod
    /// 미션으로 끈 경우의 운동.
    public var exercise: Exercise?
    public var reps: Int

    public init(
        id: UUID = UUID(),
        alarmID: UUID,
        label: String = "",
        ringStartedAt: Date,
        dismissedAt: Date,
        method: DismissMethod,
        exercise: Exercise?,
        reps: Int
    ) {
        self.id = id
        self.alarmID = alarmID
        self.label = label
        self.ringStartedAt = ringStartedAt
        self.dismissedAt = dismissedAt
        self.method = method
        self.exercise = exercise
        self.reps = reps
    }

    public var secondsToDismiss: Int { max(0, Int(dismissedAt.timeIntervalSince(ringStartedAt))) }
}

public struct WakeSummary: Equatable, Sendable {
    /// 연속 기상 일수. 오늘 아직 기록이 없어도 어제까지 이어졌으면 유지된다.
    public var streak: Int
    public var weekReps: [Exercise: Int]
    public var monthReps: [Exercise: Int]
    public var averageSecondsToDismiss: Int?
    public var monthEmergencyCount: Int
}

/// 기록 → 통계 (PRD ST-03, ST-04).
public enum WakeStats {
    /// 미션으로 알람을 끈 날(자정 기준). 긴급 해제만 한 날은 성공이 아니다.
    public static func successDays(_ records: [WakeRecord], calendar: Calendar = .current) -> Set<Date> {
        Set(records.filter { $0.method == .mission }.map { calendar.startOfDay(for: $0.ringStartedAt) })
    }

    public static func streak(_ records: [WakeRecord], today: Date, calendar: Calendar = .current) -> Int {
        let days = successDays(records, calendar: calendar)
        let start = calendar.startOfDay(for: today)
        var day = days.contains(start) ? start : calendar.date(byAdding: .day, value: -1, to: start)!
        var count = 0
        while days.contains(day) {
            count += 1
            day = calendar.date(byAdding: .day, value: -1, to: day)!
        }
        return count
    }

    public static func repsByExercise(_ records: [WakeRecord], from: Date, through: Date, calendar: Calendar = .current) -> [Exercise: Int] {
        var result: [Exercise: Int] = [:]
        for r in records where r.method == .mission {
            guard let e = r.exercise else { continue }
            let day = calendar.startOfDay(for: r.ringStartedAt)
            if day >= from && day <= through { result[e, default: 0] += r.reps }
        }
        return result
    }

    public static func summary(_ records: [WakeRecord], today: Date, calendar: Calendar = .current) -> WakeSummary {
        let todayStart = calendar.startOfDay(for: today)
        var iso = calendar
        iso.firstWeekday = 2 // 월요일 시작
        let weekStart = iso.dateInterval(of: .weekOfYear, for: todayStart)!.start
        let monthStart = calendar.dateInterval(of: .month, for: todayStart)!.start
        let missions = records.filter { $0.method == .mission }
        let avg = missions.isEmpty ? nil : missions.map(\.secondsToDismiss).reduce(0, +) / missions.count
        return WakeSummary(
            streak: streak(records, today: today, calendar: calendar),
            weekReps: repsByExercise(records, from: weekStart, through: todayStart, calendar: calendar),
            monthReps: repsByExercise(records, from: monthStart, through: todayStart, calendar: calendar),
            averageSecondsToDismiss: avg,
            monthEmergencyCount: records.filter {
                $0.method == .emergency && calendar.startOfDay(for: $0.ringStartedAt) >= monthStart
            }.count
        )
    }
}
