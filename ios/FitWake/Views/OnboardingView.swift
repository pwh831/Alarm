import AVFoundation
import SwiftUI

/// 첫 실행 안내: 소개 → 동작 방식 → 권한 → 완료.
struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var page = 0
    @State private var alarmAllowed = AlarmScheduler.isAuthorized
    @State private var cameraAllowed = AVCaptureDevice.authorizationStatus(for: .video) == .authorized

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()
            switch page {
            case 0:
                Text("운동해야 꺼지는 알람").font(.largeTitle.bold())
                Text("FitWake 알람은 버튼으로 꺼지지 않아요. 스쿼트나 푸시업을 정해진 횟수만큼 해야 꺼져요.\n\n카메라 영상은 폰 안에서만 처리되고, 서버로 보내거나 저장하지 않아요.")
            case 1:
                Text("이렇게 동작해요").font(.largeTitle.bold())
                Text("""
                1. 알람이 울리면 '미션 시작'을 눌러요.
                2. 폰을 바닥이나 선반에 세우고 2~3m 떨어져요.
                3. 몸이 인식되면 3초 뒤부터 횟수를 세요.
                4. 목표 횟수를 채우면 알람이 꺼져요.

                '중단'을 누르면 잠깐 멈추지만 1분 뒤 다시 울려요. 미션 도중 앱을 나가도 3분 안에 다시 울려요.
                """)
            default:
                Text("권한을 허용해 주세요").font(.largeTitle.bold())
                permissionRow("알람", "잠금 화면에서도 정해진 시간에 울리려면 필요해요.", allowed: alarmAllowed) {
                    Task { alarmAllowed = await AlarmScheduler.requestAuthorization() }
                }
                permissionRow("카메라", "운동 횟수를 세는 데 필요해요.", allowed: cameraAllowed) {
                    Task { cameraAllowed = await AVCaptureDevice.requestAccess(for: .video) }
                }
            }
            Spacer()
            Button(page < 2 ? "다음" : "시작하기") {
                if page < 2 { page += 1 } else { model.finishOnboarding() }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
        }
        .padding(24)
    }

    private func permissionRow(_ title: String, _ detail: String, allowed: Bool, request: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading) {
                Text(title).font(.headline)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if allowed {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            } else {
                Button("허용", action: request).buttonStyle(.bordered)
            }
        }
    }
}
