import Foundation

/// 미션 한 번의 진행.
/// - 필요한 관절이 계속 보이면 카운트다운 후 카운트를 시작한다 (PRD 3.2-4).
/// - 폰이 움직이는 동안에는 프레임을 무시한다 (PRD AC-06).
public final class MissionSession {
    public struct State: Sendable {
        /// 카운트다운 중이면 남은 초(올림), 아니면 nil.
        public var countdownSec: Int?
        public var started: Bool
        public var rep: RepState

        public init(countdownSec: Int? = nil, started: Bool = false, rep: RepState = RepState()) {
            self.countdownSec = countdownSec
            self.started = started
            self.rep = rep
        }
    }

    private let counter: RepCounter
    private let countdownMs: Int64
    private var visibleSinceMs: Int64?
    private var started = false
    private var last = RepState()

    public init(counter: RepCounter, countdownMs: Int64 = 3000) {
        self.counter = counter
        self.countdownMs = countdownMs
    }

    public func update(_ frame: PoseFrame, phoneMoving: Bool = false) -> State {
        if !started {
            if phoneMoving {
                visibleSinceMs = nil
                var rep = last
                rep.hint = .phoneMoving
                return State(countdownSec: nil, started: false, rep: rep)
            }
            if !counter.sees(frame) {
                visibleSinceMs = nil
                return State(countdownSec: nil, started: false, rep: last)
            }
            let since = visibleSinceMs ?? frame.timestampMs
            visibleSinceMs = since
            let left = countdownMs - (frame.timestampMs - since)
            if left > 0 {
                return State(countdownSec: Int((Double(left) / 1000).rounded(.up)), started: false, rep: last)
            }
            started = true
        }
        if phoneMoving {
            var rep = last
            rep.hint = .phoneMoving
            return State(countdownSec: nil, started: true, rep: rep)
        }
        last = counter.update(frame)
        return State(countdownSec: nil, started: true, rep: last)
    }
}

/// 가속도로 폰이 움직이는지 판단한다 (PRD AC-06).
/// 저역 통과 필터로 중력 방향을 추정하고, 그와 다른 가속도가 기준(m/s²)을 넘으면 한동안 "움직이는 중"으로 본다.
public final class MotionGuard {
    private let threshold: Double
    private let holdMs: Int64
    private let alpha: Double
    private var gravity: (Double, Double, Double)?
    private var lastMovementMs: Int64?

    public init(threshold: Double = 1.0, holdMs: Int64 = 700, alpha: Double = 0.1) {
        self.threshold = threshold
        self.holdMs = holdMs
        self.alpha = alpha
    }

    /// 가속도 단위는 m/s².
    public func onAccelerometer(timestampMs: Int64, x: Double, y: Double, z: Double) {
        guard var g = gravity else {
            gravity = (x, y, z)
            return
        }
        g.0 += alpha * (x - g.0)
        g.1 += alpha * (y - g.1)
        g.2 += alpha * (z - g.2)
        gravity = g
        let d = (x - g.0, y - g.1, z - g.2)
        if (d.0 * d.0 + d.1 * d.1 + d.2 * d.2).squareRoot() > threshold {
            lastMovementMs = timestampMs
        }
    }

    public func isMoving(nowMs: Int64) -> Bool {
        guard let last = lastMovementMs else { return false }
        return nowMs - last < holdMs
    }
}
