import Foundation

public enum Exercise: String, CaseIterable, Codable, Sendable {
    case pushup = "PUSHUP"
    case squat = "SQUAT"
    /// 부상이나 공간 제약이 있을 때 쓰는 저강도 대체 미션 (PRD MS-10).
    case armRaise = "ARM_RAISE"
}

/// PRD 5.3 난이도별 기준.
public enum Difficulty: String, CaseIterable, Codable, Sendable {
    case easy = "EASY"
    case normal = "NORMAL"
    case hard = "HARD"

    public var squatBottomKneeDeg: Double {
        switch self { case .easy: 120; case .normal: 100; case .hard: 85 }
    }

    public var pushupBottomElbowDeg: Double {
        switch self { case .easy: 110; case .normal: 90; case .hard: 75 }
    }

    public var allowKneePushup: Bool { self == .easy }

    /// 팔 올리기: 엉덩이-어깨-손목 각도가 이 이상이어야 1회.
    public var armRaiseMinShoulderDeg: Double {
        switch self { case .easy: 90; case .normal: 140; case .hard: 160 }
    }
}

public struct CounterConfig: Sendable {
    /// 이 신뢰도 미만의 관절은 보이지 않는 것으로 본다 (AC-01).
    public var minConfidence: Double
    /// 이보다 빠른 반복은 인정하지 않는다 (AC-03).
    public var minRepMs: Int64
    /// 몸이 이 시간 이상 안 보이면 안내를 띄우고 진행 중인 반복을 초기화한다.
    public var lostVisibilityMs: Int64
    /// 각도 지수이동평균 계수. 1이면 스무딩 없음.
    public var smoothing: Double

    public init(minConfidence: Double = 0.5, minRepMs: Int64 = 400, lostVisibilityMs: Int64 = 1000, smoothing: Double = 0.5) {
        self.minConfidence = minConfidence
        self.minRepMs = minRepMs
        self.lostVisibilityMs = lostVisibilityMs
        self.smoothing = smoothing
    }
}

public enum Phase: Sendable {
    /// 아직 시작 자세(서기/팔 편 상태)가 확인되지 않음.
    case waiting
    case top
    case bottom
}

public enum Hint: Sendable {
    case none
    case bodyNotVisible
    case tooFast
    /// 스쿼트: 무릎만 굽히고 엉덩이가 내려가지 않음.
    case lowerHips
    /// 푸시업: 몸이 수평에 가깝지 않음.
    case getHorizontal
    /// 푸시업: 엉덩이가 처지거나 솟음.
    case keepBodyStraight
    /// 폰이 움직이는 중이라 카운트를 멈춤 (PRD AC-06).
    case phoneMoving
}

public struct RepState: Sendable {
    public var reps: Int
    public var phase: Phase
    public var hint: Hint
    /// 판정에 쓰인 (스무딩된) 관절 각도. 몸이 안 보이면 nil.
    public var angleDeg: Double?

    public init(reps: Int = 0, phase: Phase = .waiting, hint: Hint = .none, angleDeg: Double? = nil) {
        self.reps = reps
        self.phase = phase
        self.hint = hint
        self.angleDeg = angleDeg
    }
}

public struct PoseMeasurement {
    public var angleDeg: Double
    /// none이 아니면 BOTTOM 진입을 막는다.
    public var formHint: Hint

    public init(angleDeg: Double, formHint: Hint = .none) {
        self.angleDeg = angleDeg
        self.formHint = formHint
    }
}

/// 관절 각도 기반 반복 카운터 (PRD 5.2).
///
/// 각도가 `topDeg` 이상이면 TOP, `bottomDeg` 이하이면 BOTTOM이고,
/// TOP → BOTTOM → TOP을 한 번 거치면 1회로 센다. 두 기준 사이의 간격이 히스테리시스 역할을 한다.
open class RepCounter {
    public let config: CounterConfig

    open var topDeg: Double { fatalError("override") }
    open var bottomDeg: Double { fatalError("override") }

    /// 필요한 관절이 안 보이면 nil.
    open func measure(_ frame: PoseFrame, smooth: (Double) -> Double) -> PoseMeasurement? { fatalError("override") }

    /// TOP 자세가 확인된 프레임마다 호출된다.
    open func onTop(_ frame: PoseFrame) {}

    open func reset() {}

    public private(set) var reps = 0
    private var phase: Phase = .waiting
    private var smoothed: Double?
    private var lastTopMs: Int64 = 0
    private var lastSeenMs: Int64?
    private var lastHint: Hint = .none

    public init(config: CounterConfig) {
        self.config = config
    }

    /// 이 프레임에서 판정에 필요한 관절이 모두 보이는지. 상태는 바꾸지 않는다.
    public func sees(_ frame: PoseFrame) -> Bool {
        measure(frame) { $0 } != nil
    }

    public func update(_ frame: PoseFrame) -> RepState {
        let now = frame.timestampMs
        let m = measure(frame) { raw in
            let s = smoothed.map { $0 + config.smoothing * (raw - $0) } ?? raw
            smoothed = s
            return s
        }

        guard let m else {
            let seen = lastSeenMs ?? now
            lastSeenMs = seen
            if now - seen >= config.lostVisibilityMs {
                phase = .waiting
                smoothed = nil
                reset()
                lastHint = .bodyNotVisible
            }
            return RepState(reps: reps, phase: phase, hint: lastHint, angleDeg: nil)
        }
        lastSeenMs = now
        if lastHint == .bodyNotVisible { lastHint = .none }

        let angle = m.angleDeg
        switch phase {
        case .waiting, .top:
            if angle >= topDeg {
                phase = .top
                lastTopMs = now
                onTop(frame)
                if lastHint != .tooFast { lastHint = m.formHint }
            } else if phase == .top && angle <= bottomDeg {
                if m.formHint == .none {
                    phase = .bottom
                    lastHint = .none
                } else {
                    lastHint = m.formHint
                }
            } else if m.formHint != .none {
                lastHint = m.formHint
            }
        case .bottom:
            if angle >= topDeg {
                if now - lastTopMs >= config.minRepMs {
                    reps += 1
                    lastHint = .none
                } else {
                    lastHint = .tooFast
                }
                phase = .top
                lastTopMs = now
                onTop(frame)
            }
        }
        return RepState(reps: reps, phase: phase, hint: lastHint, angleDeg: angle)
    }

    public static func create(_ exercise: Exercise, _ difficulty: Difficulty, config: CounterConfig = CounterConfig()) -> RepCounter {
        switch exercise {
        case .squat: return SquatCounter(difficulty: difficulty, config: config)
        case .pushup: return PushupCounter(difficulty: difficulty, config: config)
        case .armRaise: return ArmRaiseCounter(difficulty: difficulty, config: config)
        }
    }
}

/// 스쿼트: 무릎 각도(엉덩이-무릎-발목). 선 자세 대비 엉덩이가 허벅지 길이의 일정 비율 이상 내려가야 BOTTOM으로 인정한다.
public final class SquatCounter: RepCounter {
    private let difficulty: Difficulty
    private let minHipDropRatio: Double
    private var standingHipY: Double?
    private var standingThigh: Double?

    public init(difficulty: Difficulty, config: CounterConfig = CounterConfig(), minHipDropRatio: Double = 0.3) {
        self.difficulty = difficulty
        self.minHipDropRatio = minHipDropRatio
        super.init(config: config)
    }

    override public var topDeg: Double { 160 }
    override public var bottomDeg: Double { difficulty.squatBottomKneeDeg }

    override public func measure(_ frame: PoseFrame, smooth: (Double) -> Double) -> PoseMeasurement? {
        let legs = Side.allCases.compactMap { frame.visible(config.minConfidence, $0.hip, $0.knee, $0.ankle) }
        if legs.isEmpty { return nil }
        let angle = smooth(legs.map { angleDeg($0[0], $0[1], $0[2]) }.average)
        let hipY = legs.map { $0[0].y }.average
        var hint = Hint.none
        if angle <= bottomDeg, let top = standingHipY, let thigh = standingThigh, hipY - top < thigh * minHipDropRatio {
            hint = .lowerHips
        }
        return PoseMeasurement(angleDeg: angle, formHint: hint)
    }

    override public func onTop(_ frame: PoseFrame) {
        let legs = Side.allCases.compactMap { frame.visible(config.minConfidence, $0.hip, $0.knee) }
        if legs.isEmpty { return }
        standingHipY = legs.map { $0[0].y }.average
        standingThigh = legs.map { distance($0[0], $0[1]) }.average
    }

    override public func reset() {
        standingHipY = nil
        standingThigh = nil
    }
}

/// 푸시업: 팔꿈치 각도(어깨-팔꿈치-손목). 측면 촬영, 더 잘 보이는 쪽 팔을 쓴다.
/// 몸이 수평에 가까워야 하고, 어깨-엉덩이-발목이 일직선이어야 한다.
public final class PushupCounter: RepCounter {
    private let difficulty: Difficulty
    private let minBodyLineDeg: Double
    private let maxBodyTiltDeg: Double

    public init(difficulty: Difficulty, config: CounterConfig = CounterConfig(), minBodyLineDeg: Double = 150, maxBodyTiltDeg: Double = 45) {
        self.difficulty = difficulty
        self.minBodyLineDeg = minBodyLineDeg
        self.maxBodyTiltDeg = maxBodyTiltDeg
        super.init(config: config)
    }

    override public var topDeg: Double { 150 }
    override public var bottomDeg: Double { difficulty.pushupBottomElbowDeg }

    override public func measure(_ frame: PoseFrame, smooth: (Double) -> Double) -> PoseMeasurement? {
        let candidates = Side.allCases.filter { frame.visible(config.minConfidence, $0.shoulder, $0.elbow, $0.wrist, $0.hip) != nil }
        guard let side = candidates.max(by: { minConfidence(frame, $0) < minConfidence(frame, $1) }),
              let arm = frame.visible(0, side.shoulder, side.elbow, side.wrist, side.hip)
        else { return nil }
        let (shoulder, elbow, wrist, hip) = (arm[0], arm[1], arm[2], arm[3])
        let ankle = frame.visible(config.minConfidence, side.ankle)?.first
        let knee = frame.visible(config.minConfidence, side.knee)?.first

        let fullPlank = ankle.flatMap { angleDeg(shoulder, hip, $0) >= minBodyLineDeg ? $0 : nil }
        let kneePlank = knee.flatMap { difficulty.allowKneePushup && angleDeg(shoulder, hip, $0) >= minBodyLineDeg ? $0 : nil }
        guard let end = fullPlank ?? kneePlank ?? ankle ?? knee else { return nil }

        let angle = smooth(angleDeg(shoulder, elbow, wrist))
        let tilt = atan2(abs(end.y - shoulder.y), abs(end.x - shoulder.x)) * 180 / .pi
        let hint: Hint
        if tilt > maxBodyTiltDeg {
            hint = .getHorizontal
        } else if fullPlank == nil && kneePlank == nil {
            hint = .keepBodyStraight
        } else {
            hint = .none
        }
        return PoseMeasurement(angleDeg: angle, formHint: hint)
    }

    private func minConfidence(_ frame: PoseFrame, _ s: Side) -> Double {
        [s.shoulder, s.elbow, s.wrist, s.hip].map { frame.points[$0]?.confidence ?? 0 }.min() ?? 0
    }
}

/// 팔 올리기: `180 - 엉덩이-어깨-손목 각도`로 판정해서 "팔을 내린 상태"가 TOP이 되게 한다.
/// 양팔이 보이면 덜 올린 팔을 기준으로 삼아 한 팔만 드는 요령을 막는다.
public final class ArmRaiseCounter: RepCounter {
    private let difficulty: Difficulty

    public init(difficulty: Difficulty, config: CounterConfig = CounterConfig()) {
        self.difficulty = difficulty
        super.init(config: config)
    }

    override public var topDeg: Double { 150 }
    override public var bottomDeg: Double { 180 - difficulty.armRaiseMinShoulderDeg }

    override public func measure(_ frame: PoseFrame, smooth: (Double) -> Double) -> PoseMeasurement? {
        let arms = Side.allCases.compactMap { frame.visible(config.minConfidence, $0.hip, $0.shoulder, $0.wrist) }
        if arms.isEmpty { return nil }
        let lowestArm = arms.map { angleDeg($0[0], $0[1], $0[2]) }.min()!
        return PoseMeasurement(angleDeg: smooth(180 - lowestArm))
    }
}

extension Array where Element == Double {
    var average: Double { isEmpty ? 0 : reduce(0, +) / Double(count) }
}
