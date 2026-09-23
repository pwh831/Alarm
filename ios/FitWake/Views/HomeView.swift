import FitWakeCore
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editing: WakeAlarm?
    @State private var now = Date()
    private let clock = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if let next = model.alarms.nextAlarm(after: now) {
                        Text(remainingText(until: next.at, from: now)).foregroundStyle(.orange)
                    } else {
                        Text("켜진 알람이 없어요").foregroundStyle(.secondary)
                    }
                    if !AlarmScheduler.isAuthorized {
                        Button("알람 권한 허용하기") {
                            Task {
                                await AlarmScheduler.requestAuthorization()
                                await model.resyncAlarms()
                                model.reload()
                            }
                        }
                    }
                }
                Section {
                    if model.alarms.isEmpty {
                        Text("아직 알람이 없어요. 오른쪽 위 + 버튼으로 만들어 보세요.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(model.alarms) { alarm in
                        AlarmRow(alarm: alarm) { enabled in
                            Task { await model.setEnabled(alarm, enabled) }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { editing = alarm }
                    }
                    .onDelete { indexSet in
                        indexSet.map { model.alarms[$0] }.forEach(model.delete)
                    }
                }
            }
            .navigationTitle("FitWake")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    NavigationLink("기록") { StatsView() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editing = WakeAlarm() } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $editing) { alarm in
                EditAlarmView(alarm: alarm, isNew: !model.alarms.contains { $0.id == alarm.id })
            }
            .alert("알림", isPresented: Binding(get: { model.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil } })) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(model.errorMessage ?? "")
            }
            .onReceive(clock) { now = $0 }
        }
    }
}

private struct AlarmRow: View {
    let alarm: WakeAlarm
    let onToggle: (Bool) -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(alarm.timeText).font(.system(size: 44, weight: .light, design: .rounded))
                Text([alarm.label, alarm.repeatText].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.subheadline)
                Text(alarm.missionText).font(.caption).foregroundStyle(.orange)
            }
            .opacity(alarm.enabled ? 1 : 0.5)
            Spacer()
            Toggle("", isOn: Binding(get: { alarm.enabled }, set: onToggle)).labelsHidden()
        }
    }
}
