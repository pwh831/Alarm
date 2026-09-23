import AVFoundation
import CoreMotion
import FitWakeCore
import UIKit

struct MissionConfig: Equatable {
    var exercise: Exercise
    var difficulty: Difficulty
    var targetReps: Int
}

/// 미션 한 번: 카메라 → 자세 인식 → 반복 카운트, 폰 움직임 감지, 음성 안내, (알람일 때) 알람음.
@MainActor
final class MissionModel: ObservableObject {
    @Published private(set) var state = MissionSession.State()
    @Published private(set) var points: [Landmark: CGPoint] = [:]
    @Published private(set) var imageSize = CGSize(width: 720, height: 1280)
    @Published private(set) var elapsedSeconds: Int?
    @Published var useFrontCamera = true {
        didSet { camera.configure(position: useFrontCamera ? .front : .back) }
    }
    @Published var torchOn = false {
        didSet { camera.setTorch(torchOn && !useFrontCamera) }
    }

    let config: MissionConfig
    let camera = PoseCamera()
    private let session: MissionSession
    private let motion = CMMotionManager()
    private let motionGuard = MotionGuard()
    private let speech = AVSpeechSynthesizer()
    private let volumePolicy = RingVolumePolicy()
    private var tone: TonePlayer?
    private var lastProgressMs: Int64 = 0
    private var startedAt = Date()
    private var heartbeat: Timer?
    private var volumeTimer: Timer?
    private var savedBrightness: CGFloat = 0.5

    /// [ringing]이면 미션 중에도 알람음을 계속 울린다 (줄어든 볼륨으로).
    init(config: MissionConfig, ringing: Bool) {
        self.config = config
        // Vision의 신뢰도는 ML Kit보다 낮게 나오는 편이라 기준을 낮춘다.
        let counter = RepCounter.create(config.exercise, config.difficulty, config: CounterConfig(minConfidence: 0.3))
        session = MissionSession(counter: counter)
        if ringing { tone = TonePlayer() }
    }

    func start() {
        startedAt = Date()
        lastProgressMs = nowMs
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)

        camera.onResult = { [weak self] result in self?.handle(result) }
        camera.configure(position: useFrontCamera ? .front : .back)
        camera.start()

        if motion.isAccelerometerAvailable {
            motion.accelerometerUpdateInterval = 1.0 / 50
            motion.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
                guard let self, let a = data?.acceleration else { return }
                // CoreMotion 단위는 g.
                self.motionGuard.onAccelerometer(timestampMs: self.nowMs, x: a.x * 9.81, y: a.y * 9.81, z: a.z * 9.81)
            }
        }

        // 어두운 방에서 화면이 조명 역할을 하도록 밝기를 올리고, 화면이 꺼지지 않게 한다 (PRD 5.5).
        UIApplication.shared.isIdleTimerDisabled = true
        if let screen = currentScreen {
            savedBrightness = screen.brightness
            screen.brightness = 1
        }

        if let tone {
            tone.volume = Float(volumePolicy.volume(nowMs: nowMs, lastProgressMs: lastProgressMs))
            tone.play()
            volumeTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.updateVolume() }
            }
            // 미션 도중 앱을 나가면 백업 알람이 울리도록, 화면에 있는 동안은 계속 뒤로 미룬다.
            heartbeat = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { _ in
                Task { await RingCoordinator.scheduleBackup(after: RingCoordinator.missionHeartbeatSeconds) }
            }
        }
    }

    func stop() {
        camera.onResult = nil
        camera.setTorch(false)
        camera.stop()
        motion.stopAccelerometerUpdates()
        heartbeat?.invalidate()
        volumeTimer?.invalidate()
        tone?.stop()
        tone = nil
        speech.stopSpeaking(at: .immediate)
        UIApplication.shared.isIdleTimerDisabled = false
        currentScreen?.brightness = savedBrightness
    }

    private var nowMs: Int64 { Int64(CACurrentMediaTime() * 1000) }

    private var currentScreen: UIScreen? {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen
    }

    private func updateVolume() {
        tone?.volume = Float(volumePolicy.volume(nowMs: nowMs, lastProgressMs: lastProgressMs))
    }

    private func handle(_ result: PoseCamera.Result) {
        guard elapsedSeconds == nil else { return }
        points = result.normalized.filter { (result.frame.points[$0.key]?.confidence ?? 0) >= 0.3 }
        imageSize = result.imageSize
        let before = state.rep.reps
        state = session.update(result.frame, phoneMoving: motionGuard.isMoving(nowMs: nowMs))
        let reps = state.rep.reps
        guard reps > before else { return }

        lastProgressMs = nowMs
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let utterance = AVSpeechUtterance(string: "\(reps)")
        utterance.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        speech.stopSpeaking(at: .immediate)
        speech.speak(utterance)

        if reps >= config.targetReps {
            elapsedSeconds = Int(Date().timeIntervalSince(startedAt))
            stop()
        }
    }
}
