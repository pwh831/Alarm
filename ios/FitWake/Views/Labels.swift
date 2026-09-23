import FitWakeCore
import Foundation

extension Exercise {
    var label: String {
        switch self {
        case .squat: "스쿼트"
        case .pushup: "푸시업"
        case .armRaise: "팔 올리기"
        }
    }

    var defaultReps: Int {
        switch self {
        case .pushup: 10
        case .squat: 15
        case .armRaise: 20
        }
    }
}

extension Optional where Wrapped == Exercise {
    /// nil은 랜덤 미션.
    var label: String { self?.label ?? "랜덤" }
}

extension Difficulty {
    var label: String {
        switch self {
        case .easy: "쉬움"
        case .normal: "보통"
        case .hard: "어려움"
        }
    }
}

extension Weekday {
    var shortLabel: String {
        switch self {
        case .monday: "월"
        case .tuesday: "화"
        case .wednesday: "수"
        case .thursday: "목"
        case .friday: "금"
        case .saturday: "토"
        case .sunday: "일"
        }
    }
}

extension WakeAlarm {
    var timeText: String { String(format: "%02d:%02d", hour, minute) }

    var repeatText: String {
        if repeatDays.isEmpty { return "한 번만" }
        if repeatDays.count == 7 { return "매일" }
        if repeatDays == [.saturday, .sunday] { return "주말" }
        if repeatDays == [.monday, .tuesday, .wednesday, .thursday, .friday] { return "평일" }
        return repeatDays.sorted().map(\.shortLabel).joined(separator: " ")
    }

    var missionText: String { "\(exercise.label) \(targetReps)회 · \(difficulty.label)" }
}

func remainingText(until date: Date, from now: Date = Date()) -> String {
    let minutes = max(1, Int((date.timeIntervalSince(now) + 59) / 60))
    let d = minutes / (24 * 60), h = (minutes / 60) % 24, m = minutes % 60
    if d > 0 { return "\(d)일 \(h)시간 후에 울려요" }
    if h > 0 { return "\(h)시간 \(m)분 후에 울려요" }
    return "\(m)분 후에 울려요"
}
