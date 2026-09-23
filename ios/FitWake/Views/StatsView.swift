import FitWakeCore
import SwiftUI

/// 기록·통계 (PRD ST-01~ST-04).
struct StatsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var month = Calendar.current.dateInterval(of: .month, for: Date())!.start

    var body: some View {
        let today = Date()
        let summary = WakeStats.summary(model.records, today: today)
        let successDays = WakeStats.successDays(model.records)
        List {
            Section {
                VStack {
                    Text("연속 기상").font(.subheadline)
                    Text("\(summary.streak)일").font(.system(size: 48, weight: .bold)).foregroundStyle(.orange)
                }
                .frame(maxWidth: .infinity)
            }
            Section("운동량") {
                repsRow("이번 주", summary.weekReps)
                repsRow("이번 달", summary.monthReps)
                if let avg = summary.averageSecondsToDismiss {
                    Text("평균 \(avg / 60)분 \(avg % 60)초 만에 알람을 껐어요")
                }
                Text("이번 달 긴급 해제 \(summary.monthEmergencyCount)번")
            }
            Section {
                MonthCalendar(month: $month, successDays: successDays, today: today)
            }
            Section("최근 기록") {
                if model.records.isEmpty {
                    Text("아직 기록이 없어요. 알람이 울리고 미션을 마치면 여기에 쌓여요.").foregroundStyle(.secondary)
                }
                ForEach(model.records.prefix(30)) { r in
                    VStack(alignment: .leading) {
                        Text(r.ringStartedAt.formatted(date: .abbreviated, time: .shortened))
                        Text(r.method == .mission
                             ? "\(r.exercise.label) \(r.reps)회 · \(r.secondsToDismiss / 60)분 \(r.secondsToDismiss % 60)초"
                             : "긴급 해제")
                            .font(.caption)
                            .foregroundStyle(r.method == .mission ? .orange : .red)
                    }
                }
            }
        }
        .navigationTitle("기록")
    }

    private func repsRow(_ title: String, _ reps: [Exercise: Int]) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(reps.isEmpty ? "-" : Exercise.allCases.compactMap { e in reps[e].map { "\(e.label) \($0)회" } }.joined(separator: ", "))
                .foregroundStyle(.secondary)
        }
    }
}

/// 미션으로 일어난 날을 표시하는 달력 (월요일 시작).
private struct MonthCalendar: View {
    @Binding var month: Date
    let successDays: Set<Date>
    let today: Date
    private let calendar = Calendar.current

    var body: some View {
        let days = calendar.range(of: .day, in: .month, for: month)!.count
        let leading = Weekday(calendarWeekday: calendar.component(.weekday, from: month)).rawValue - 1
        let cells: [Date?] = Array(repeating: nil, count: leading)
            + (0..<days).map { calendar.date(byAdding: .day, value: $0, to: month) }
        VStack(spacing: 6) {
            HStack {
                Button { shift(-1) } label: { Image(systemName: "chevron.left") }
                Spacer()
                Text(month.formatted(.dateTime.year().month()))
                Spacer()
                Button { shift(1) } label: { Image(systemName: "chevron.right") }
                    .disabled(calendar.isDate(month, equalTo: today, toGranularity: .month))
            }
            .buttonStyle(.borderless)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 6) {
                ForEach(Weekday.allCases, id: \.self) { Text($0.shortLabel).font(.caption2).foregroundStyle(.secondary) }
                ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                    if let day {
                        let success = successDays.contains(calendar.startOfDay(for: day))
                        Text("\(calendar.component(.day, from: day))")
                            .font(.caption)
                            .frame(width: 30, height: 30)
                            .background(success ? Color.orange : (calendar.isDate(day, inSameDayAs: today) ? Color.gray.opacity(0.3) : .clear), in: Circle())
                            .foregroundStyle(success ? .black : .primary)
                    } else {
                        Color.clear.frame(height: 30)
                    }
                }
            }
        }
    }

    private func shift(_ months: Int) {
        month = calendar.date(byAdding: .month, value: months, to: month) ?? month
    }
}
