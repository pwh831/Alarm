import AVFoundation
import FitWakeCore
import SwiftUI

/// 카메라 미리보기 + 관절 표시 + 횟수 + 안내 문구.
struct MissionView<Footer: View>: View {
    @ObservedObject var model: MissionModel
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        ZStack {
            CameraPreview(session: model.camera.session)
                .ignoresSafeArea()
            SkeletonOverlay(points: model.points, imageSize: model.imageSize)
                .ignoresSafeArea()

            if let sec = model.state.countdownSec {
                Text("\(sec)")
                    .font(.system(size: 160, weight: .bold))
                    .foregroundStyle(.white)
                    .shadow(radius: 8)
            }

            VStack(spacing: 12) {
                Text("\(model.state.rep.reps) / \(model.config.targetReps)")
                    .font(.system(size: 72, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 24))
                Text(hintText)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 12))
                Spacer()
                HStack {
                    Button(model.useFrontCamera ? "후면 카메라" : "전면 카메라") { model.useFrontCamera.toggle() }
                    if !model.useFrontCamera {
                        Button(model.torchOn ? "플래시 끄기" : "플래시 켜기") { model.torchOn.toggle() }
                    }
                    Spacer()
                    footer()
                }
                .buttonStyle(.borderedProminent)
                .tint(.black.opacity(0.6))
            }
            .padding()
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
    }

    private var hintText: String {
        let s = model.state
        switch s.rep.hint {
        case .bodyNotVisible: return "몸이 잘 안 보여요. 조금 더 뒤로 가거나 불을 켜주세요"
        case .tooFast: return "너무 빨라요. 천천히 끝까지 해주세요"
        case .lowerHips: return "엉덩이를 더 내려주세요"
        case .getHorizontal: return "몸을 바닥과 수평으로 만들어주세요"
        case .keepBodyStraight: return "어깨부터 발끝까지 일직선을 유지하세요"
        case .phoneMoving: return "폰이 움직이고 있어요. 바닥이나 선반에 세워 두세요"
        case .none:
            if s.countdownSec != nil { return "좋아요, 그대로! 곧 시작해요" }
            if s.started && s.rep.phase != .waiting { return "좋아요! 계속하세요" }
            switch model.config.exercise {
            case .squat: return "전신이 보이도록 2~3m 떨어져서 똑바로 서세요"
            case .pushup: return "폰을 옆에 두고 팔을 편 플랭크 자세를 잡으세요"
            case .armRaise: return "상체가 다 보이게 서서 양팔을 내리세요"
            }
        }
    }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        if let connection = view.previewLayer.connection, connection.isVideoRotationAngleSupported(90) {
            connection.videoRotationAngle = 90
        }
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {
        if let connection = view.previewLayer.connection, connection.isVideoRotationAngleSupported(90) {
            connection.videoRotationAngle = 90
        }
    }
}

/// 인식된 관절을 미리보기 위에 그린다 (PRD MS-06). 미리보기와 같은 aspect-fill 변환을 쓴다.
struct SkeletonOverlay: View {
    let points: [Landmark: CGPoint]
    let imageSize: CGSize

    private static let bones: [(Landmark, Landmark)] = [
        (.leftShoulder, .rightShoulder), (.leftHip, .rightHip),
        (.leftShoulder, .leftElbow), (.leftElbow, .leftWrist),
        (.rightShoulder, .rightElbow), (.rightElbow, .rightWrist),
        (.leftShoulder, .leftHip), (.rightShoulder, .rightHip),
        (.leftHip, .leftKnee), (.leftKnee, .leftAnkle),
        (.rightHip, .rightKnee), (.rightKnee, .rightAnkle),
    ]

    var body: some View {
        Canvas { context, size in
            let scale = max(size.width / imageSize.width, size.height / imageSize.height)
            let dx = (size.width - imageSize.width * scale) / 2
            let dy = (size.height - imageSize.height * scale) / 2
            func map(_ p: CGPoint) -> CGPoint {
                CGPoint(x: p.x * imageSize.width * scale + dx, y: p.y * imageSize.height * scale + dy)
            }
            for (a, b) in Self.bones {
                guard let pa = points[a], let pb = points[b] else { continue }
                var path = Path()
                path.move(to: map(pa))
                path.addLine(to: map(pb))
                context.stroke(path, with: .color(.orange), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            }
            for p in points.values {
                let c = map(p)
                context.fill(Path(ellipseIn: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12)), with: .color(.white))
            }
        }
        .allowsHitTesting(false)
    }
}

/// 카메라 권한이 없으면 요청하고, 거부됐으면 안내한다.
struct CameraPermissionGate<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @State private var status = AVCaptureDevice.authorizationStatus(for: .video)

    var body: some View {
        switch status {
        case .authorized:
            content()
        case .notDetermined:
            ProgressView()
                .task {
                    _ = await AVCaptureDevice.requestAccess(for: .video)
                    status = AVCaptureDevice.authorizationStatus(for: .video)
                }
        default:
            VStack(spacing: 16) {
                Text("운동을 인식하려면 카메라 권한이 필요해요.\n설정 앱 → FitWake → 카메라를 켜 주세요.")
                    .multilineTextAlignment(.center)
                Button("설정 열기") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
            }
            .padding()
        }
    }
}
