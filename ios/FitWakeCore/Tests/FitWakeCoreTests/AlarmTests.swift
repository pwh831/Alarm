import XCTest
@testable import FitWakeCore

final class WakeAlarmTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()

    // 2026-09-23은 수요일
    private func at(_ day: Int, _ h: Int, _ m: Int, _ s: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 9, day: day, hour: h, minute: m, second: s))!
    }

    func testWeekdayConversion() {
        XCTAssertEqual(Weekday(calendarWeekday: 1), .sunday)
        XCTAssertEqual(Weekday(calendarWeekday: 2), .monday)
        XCTAssertEqual(Weekday(calendarWeekday: 7), .saturday)
    }

    func testOneShot() {
        let alarm = WakeAlarm(hour: 7, minute: 0)
        XCTAssertEqual(alarm.nextTrigger(after: at(23, 6, 30), calendar: calendar), at(23, 7, 0))
        XCTAssertEqual(alarm.nextTrigger(after: at(23, 7, 30), calendar: calendar), at(24, 7, 0))
        XCTAssertEqual(alarm.nextTrigger(after: at(23, 7, 0), calendar: calendar), at(24, 7, 0))
    }

    func testWeekdaysSkipWeekend() {
        let alarm = WakeAlarm(hour: 6, minute: 30, repeatDays: [.monday, .tuesday, .wednesday, .thursday, .friday])
        XCTAssertEqual(alarm.nextTrigger(after: at(25, 7, 0), calendar: calendar), at(28, 6, 30))
        XCTAssertEqual(alarm.nextTrigger(after: at(23, 6, 0), calendar: calendar), at(23, 6, 30))
    }

    func testNextAlarmPicksEarliestEnabled() {
        let a = WakeAlarm(hour: 7, minute: 0)
        let b = WakeAlarm(hour: 6, minute: 0, enabled: false)
        let c = WakeAlarm(hour: 23, minute: 0)
        XCTAssertEqual([a, b, c].nextAlarm(after: at(23, 22, 0), calendar: calendar)?.alarm.id, c.id)
        XCTAssertNil([b].nextAlarm(after: at(23, 22, 0), calendar: calendar))
    }

    func testRandomPicksFromPool() {
        let alarm = WakeAlarm(exercise: nil)
        let picked = Set((0..<50).map { _ in alarm.pickExercise() })
        XCTAssertEqual(picked, Set(WakeAlarm.randomExercises))
    }

    func testCodableRoundTrip() throws {
        let alarm = WakeAlarm(hour: 5, minute: 45, repeatDays: [.saturday], label: "운동", exercise: nil, difficulty: .hard, targetReps: 20)
        let decoded = try JSONDecoder().decode(WakeAlarm.self, from: JSONEncoder().encode(alarm))
        XCTAssertEqual(decoded, alarm)
    }

    func testEmergencyPhrase() {
        XCTAssertTrue(EmergencyDismiss.matches("  나는 지금  완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다 "))
        XCTAssertFalse(EmergencyDismiss.matches("나는 지금 완전히 깨어 있고"))
    }
}

final class WakeStatsTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()

    private var today: Date { calendar.date(from: DateComponents(year: 2026, month: 9, day: 23, hour: 12))! }

    private func rec(_ daysAgo: Int, _ method: DismissMethod = .mission, _ exercise: Exercise? = .squat, reps: Int = 15, seconds: Double = 90) -> WakeRecord {
        let start = calendar.date(byAdding: .day, value: -daysAgo, to: calendar.date(from: DateComponents(year: 2026, month: 9, day: 23, hour: 7))!)!
        return WakeRecord(alarmID: UUID(), ringStartedAt: start, dismissedAt: start.addingTimeInterval(seconds), method: method, exercise: exercise, reps: reps)
    }

    func testStreak() {
        XCTAssertEqual(WakeStats.streak([rec(0), rec(1), rec(2), rec(4)], today: today, calendar: calendar), 3)
        XCTAssertEqual(WakeStats.streak([rec(1), rec(2)], today: today, calendar: calendar), 2)
        XCTAssertEqual(WakeStats.streak([rec(2)], today: today, calendar: calendar), 0)
        XCTAssertEqual(WakeStats.streak([rec(0), rec(1, .emergency, nil), rec(2)], today: today, calendar: calendar), 1)
    }

    func testSummary() {
        let records = [
            rec(0, .mission, .squat, reps: 15, seconds: 60),
            rec(1, .mission, .pushup, reps: 10, seconds: 120),
            rec(3, .mission, .squat, reps: 20), // 9/20 일요일: 지난주
            rec(22, .mission, .squat, reps: 30), // 9/1
            rec(23, .mission, .squat, reps: 99), // 8/31: 지난달
            rec(2, .emergency, nil, reps: 0),
        ]
        let s = WakeStats.summary(records, today: today, calendar: calendar)
        XCTAssertEqual(s.weekReps, [.squat: 15, .pushup: 10])
        XCTAssertEqual(s.monthReps, [.squat: 65, .pushup: 10])
        XCTAssertEqual(s.monthEmergencyCount, 1)
        XCTAssertEqual(s.streak, 2)
    }
}
