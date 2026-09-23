import Foundation

/// 월요일 = 1 ... 일요일 = 7 (ISO 8601).
public enum Weekday: Int, CaseIterable, Codable, Comparable, Sendable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday

    /// `Calendar`의 weekday(일요일 = 1)에서 변환.
    public init(calendarWeekday: Int) {
        self.init(rawValue: (calendarWeekday + 5) % 7 + 1)!
    }

    public static func < (a: Weekday, b: Weekday) -> Bool { a.rawValue < b.rawValue }
}

/// 사용자가 만든 알람 하나.
public struct WakeAlarm: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var hour: Int
    public var minute: Int
    /// 비어 있으면 한 번만 울리는 알람.
    public var repeatDays: Set<Weekday>
    public var label: String
    public var enabled: Bool
    /// nil이면 울릴 때마다 무작위 (PRD MS-07).
    public var exercise: Exercise?
    public var difficulty: Difficulty
    public var targetReps: Int

    public init(
        id: UUID = UUID(),
        hour: Int = 7,
        minute: Int = 0,
        repeatDays: Set<Weekday> = [],
        label: String = "",
        enabled: Bool = true,
        exercise: Exercise? = .squat,
        difficulty: Difficulty = .normal,
        targetReps: Int = 15
    ) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.repeatDays = repeatDays
        self.label = label
        self.enabled = enabled
        self.exercise = exercise
        self.difficulty = difficulty
        self.targetReps = targetReps
    }

    public var isRepeating: Bool { !repeatDays.isEmpty }

    /// 랜덤 미션 후보. 팔 올리기는 사용자가 직접 골랐을 때만 쓴다.
    public static let randomExercises: [Exercise] = [.squat, .pushup]

    /// 이번에 할 운동. 랜덤 미션이면 여기서 정해진다.
    public func pickExercise<G: RandomNumberGenerator>(using rng: inout G) -> Exercise {
        exercise ?? Self.randomExercises.randomElement(using: &rng)!
    }

    public func pickExercise() -> Exercise {
        var rng = SystemRandomNumberGenerator()
        return pickExercise(using: &rng)
    }

    /// `now` 이후(같은 시각 제외) 처음 울릴 시각.
    public func nextTrigger(after now: Date, calendar: Calendar = .current) -> Date {
        let today = calendar.startOfDay(for: now)
        for offset in 0...7 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            if isRepeating && !repeatDays.contains(Weekday(calendarWeekday: calendar.component(.weekday, from: day))) {
                continue
            }
            if let candidate = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day), candidate > now {
                return candidate
            }
        }
        fatalError("a matching day always exists within 8 days")
    }
}

extension Array where Element == WakeAlarm {
    /// 켜진 알람 중 가장 먼저 울릴 알람과 그 시각.
    public func nextAlarm(after now: Date, calendar: Calendar = .current) -> (alarm: WakeAlarm, at: Date)? {
        filter(\.enabled)
            .map { ($0, $0.nextTrigger(after: now, calendar: calendar)) }
            .min { $0.1 < $1.1 }
    }
}

/// 긴급 해제 (PRD AC-07): 긴 문장을 정확히 따라 입력해야 한다. 띄어쓰기 차이는 봐준다.
public enum EmergencyDismiss {
    public static let phrase = "나는 지금 완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다"

    public static func matches(_ input: String, phrase: String = phrase) -> Bool {
        normalize(input) == normalize(phrase)
    }

    private static func normalize(_ s: String) -> String {
        s.split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }
}

/// 알람이 울리는 동안의 앱 안 볼륨 (0...1).
/// 미션 중에는 줄이되, 일정 시간 한 번도 성공하지 못하면 원래대로 되돌린다 (PRD 3.2).
public struct RingVolumePolicy: Sendable {
    public var missionVolume: Double = 0.35
    public var idleMs: Int64 = 60_000

    public init() {}

    public func volume(nowMs: Int64, lastProgressMs: Int64) -> Double {
        nowMs - lastProgressMs >= idleMs ? 1 : missionVolume
    }
}
