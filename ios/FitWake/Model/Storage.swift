import FitWakeCore
import Foundation

/// 알람과 기록을 JSON 파일로 저장한다. 앱과 App Intent(알람 버튼) 양쪽에서 쓴다.
enum Storage {
    private static var directory: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private static func load<T: Decodable>(_ name: String, as type: T.Type) -> T? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private static func save<T: Encodable>(_ value: T, to name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: directory.appendingPathComponent(name), options: .atomic)
    }

    static var alarms: [WakeAlarm] {
        get { load("alarms.json", as: [WakeAlarm].self) ?? [] }
        set { save(newValue, to: "alarms.json") }
    }

    static var records: [WakeRecord] {
        get { load("records.json", as: [WakeRecord].self) ?? [] }
        set { save(newValue, to: "records.json") }
    }
}

extension Notification.Name {
    /// 알람 버튼(App Intent)이나 미션 종료로 울림 상태가 바뀌었을 때.
    static let fitWakeRingChanged = Notification.Name("fitWakeRingChanged")
}
