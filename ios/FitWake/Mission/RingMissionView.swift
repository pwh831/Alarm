import FitWakeCore
import SwiftUI

/// 알람이 울린 뒤의 미션 화면. 미션을 끝내거나 긴급 해제해야만 닫힌다.
struct RingMissionView: View {
    let ring: ActiveRing
    @EnvironmentObject private var appModel: AppModel
    @StateObject private var mission: MissionModel
    @State private var showEmergency = false
    @State private var emergencyInput = ""

    init(ring: ActiveRing) {
        self.ring = ring
        _mission = StateObject(wrappedValue: MissionModel(
            config: MissionConfig(exercise: ring.exercise, difficulty: ring.difficulty, targetReps: ring.targetReps),
            ringing: true
        ))
    }

    var body: some View {
        Group {
            if let sec = mission.elapsedSeconds {
                VStack(spacing: 20) {
                    Text("미션 완료!").font(.largeTitle.bold())
                    Text("\(sec)초 만에 알람을 껐어요. 좋은 하루 보내세요!")
                    Button("닫기") { close(method: .mission, reps: ring.targetReps) }
                        .buttonStyle(.borderedProminent)
                }
                .padding()
            } else {
                CameraPermissionGate {
                    MissionView(model: mission) {
                        Button("긴급 해제") { showEmergency = true }
                    }
                }
            }
        }
        .alert("긴급 해제", isPresented: $showEmergency) {
            TextField("문장 입력", text: $emergencyInput)
            Button("알람 끄기", role: .destructive) {
                if EmergencyDismiss.matches(emergencyInput) {
                    mission.stop()
                    close(method: .emergency, reps: 0)
                }
                emergencyInput = ""
            }
            Button("취소", role: .cancel) { emergencyInput = "" }
        } message: {
            Text("정말 급할 때만 쓰세요. 아래 문장을 그대로 입력해야 꺼져요.\n\n\(EmergencyDismiss.phrase)")
        }
    }

    private func close(method: DismissMethod, reps: Int) {
        RingCoordinator.finish(method: method, reps: reps)
        appModel.reload()
    }
}
