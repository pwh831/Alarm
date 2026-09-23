import FitWakeCore
import SwiftUI

struct EditAlarmView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var alarm: WakeAlarm
    @State private var time: Date
    @State private var previewing = false
    let isNew: Bool

    init(alarm: WakeAlarm, isNew: Bool) {
        self.isNew = isNew
        _alarm = State(initialValue: alarm)
        _time = State(initialValue: Calendar.current.date(bySettingHour: alarm.hour, minute: alarm.minute, second: 0, of: Date()) ?? Date())
    }

    /// 선택지 순서. nil은 랜덤.
    private let exerciseChoices: [Exercise?] = [.squat, .pushup, nil, .armRaise]

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("시간", selection: $time, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)

                Section("반복") {
                    HStack {
                        ForEach(Weekday.allCases, id: \.self) { day in
                            let on = alarm.repeatDays.contains(day)
                            Text(day.shortLabel)
                                .frame(maxWidth: .infinity, minHeight: 36)
                                .background(on ? Color.orange : Color.gray.opacity(0.25), in: Circle())
                                .foregroundStyle(on ? .black : .primary)
                                .onTapGesture {
                                    if on { alarm.repeatDays.remove(day) } else { alarm.repeatDays.insert(day) }
                                }
                        }
                    }
                    TextField("이름 (선택)", text: $alarm.label)
                }

                Section {
                    Picker("운동", selection: Binding(get: { alarm.exercise }, set: { e in
                        alarm.exercise = e
                        alarm.targetReps = e?.defaultReps ?? 15
                    })) {
                        ForEach(exerciseChoices, id: \.self) { e in Text(e.label).tag(e) }
                    }
                    Picker("난이도", selection: $alarm.difficulty) {
                        ForEach(Difficulty.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Stepper("목표 \(alarm.targetReps)회", value: $alarm.targetReps, in: 5...50)
                    Button("미리 해보기") { previewing = true }
                } header: {
                    Text("미션")
                } footer: {
                    switch alarm.exercise {
                    case nil: Text("울릴 때마다 스쿼트와 푸시업 중 하나가 무작위로 정해져요.")
                    case .armRaise?: Text("부상이 있거나 공간이 좁을 때 쓰는 가벼운 미션이에요. 양팔을 머리 위로 올렸다 내리면 1회예요.")
                    case .pushup?: Text("폰을 몸 옆쪽에 세워 측면에서 찍히게 해 주세요.")
                    default: Text("폰을 세워 두고 전신이 보이게 2~3m 떨어져 주세요.")
                    }
                }

                if !isNew {
                    Section {
                        Button("삭제", role: .destructive) {
                            model.delete(alarm)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "새 알람" : "알람 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") {
                        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
                        alarm.hour = c.hour ?? 7
                        alarm.minute = c.minute ?? 0
                        alarm.enabled = true
                        let toSave = alarm
                        Task {
                            if !AlarmScheduler.isAuthorized { await AlarmScheduler.requestAuthorization() }
                            await model.save(toSave)
                        }
                        dismiss()
                    }
                }
            }
            .fullScreenCover(isPresented: $previewing) {
                PreviewMissionView(
                    config: MissionConfig(exercise: alarm.pickExercise(), difficulty: alarm.difficulty, targetReps: alarm.targetReps)
                )
            }
        }
    }
}

/// 저장 전 "미리 해보기" (PRD 3.1-6). 알람음 없이 언제든 그만둘 수 있다.
private struct PreviewMissionView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var mission: MissionModel

    init(config: MissionConfig) {
        _mission = StateObject(wrappedValue: MissionModel(config: config, ringing: false))
    }

    var body: some View {
        if let sec = mission.elapsedSeconds {
            VStack(spacing: 20) {
                Text("성공!").font(.largeTitle.bold())
                Text("\(sec)초 걸렸어요.")
                Button("닫기") { dismiss() }.buttonStyle(.borderedProminent)
            }
        } else {
            CameraPermissionGate {
                MissionView(model: mission) {
                    Button("그만하기") { dismiss() }
                }
            }
        }
    }
}
