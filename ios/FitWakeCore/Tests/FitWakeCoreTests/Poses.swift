import Foundation
@testable import FitWakeCore

/// 테스트용 합성 포즈. 좌표는 0...1 이미지 좌표(y 아래로 증가).
enum Poses {
    static let seg = 0.2

    /// 측면 스쿼트: 발목·무릎 고정, 무릎 각도만큼 허벅지를 회전.
    static func squat(_ kneeDeg: Double, confidence: Double = 0.9) -> [Landmark: PosePoint] {
        let ankle = PosePoint(x: 0.5, y: 0.9, confidence: confidence)
        let knee = PosePoint(x: 0.5, y: 0.7, confidence: confidence)
        let t = kneeDeg * .pi / 180
        let hip = PosePoint(x: knee.x + seg * sin(t), y: knee.y + seg * cos(t), confidence: confidence)
        let shoulder = PosePoint(x: hip.x, y: hip.y - 0.3, confidence: confidence)
        return legs(shoulder, hip, knee, ankle)
    }

    /// 무릎은 굽혔지만 엉덩이는 제자리.
    static func kneeOnlyBend(_ kneeDeg: Double) -> [Landmark: PosePoint] {
        let hip = PosePoint(x: 0.5, y: 0.5)
        let knee = PosePoint(x: 0.5, y: 0.7)
        let t = kneeDeg * .pi / 180
        let ankle = PosePoint(x: knee.x - seg * sin(t), y: knee.y - seg * cos(t))
        return legs(PosePoint(x: 0.5, y: 0.2), hip, knee, ankle)
    }

    private static func legs(_ shoulder: PosePoint, _ hip: PosePoint, _ knee: PosePoint, _ ankle: PosePoint) -> [Landmark: PosePoint] {
        var m: [Landmark: PosePoint] = [:]
        for s in Side.allCases {
            m[s.shoulder] = shoulder; m[s.hip] = hip; m[s.knee] = knee; m[s.ankle] = ankle
        }
        return m
    }

    /// 측면 푸시업: 손목 고정, 팔꿈치 각도에 따라 어깨 높이가 바뀐다.
    static func pushup(_ elbowDeg: Double, knees: Bool = false, sag: Double = 0) -> [Landmark: PosePoint] {
        let arm = 0.1
        let wrist = PosePoint(x: 0.3, y: 0.8)
        let half = elbowDeg / 2 * .pi / 180
        let shoulder = PosePoint(x: 0.3, y: wrist.y - 2 * arm * sin(half))
        let elbow = PosePoint(x: 0.3 - arm * cos(half), y: (shoulder.y + wrist.y) / 2)
        let knee: PosePoint
        let ankle: PosePoint
        if knees {
            knee = PosePoint(x: 0.65, y: 0.8)
            ankle = PosePoint(x: 0.9, y: 0.6)
        } else {
            knee = PosePoint(x: 0.65, y: shoulder.y + (0.8 - shoulder.y) * 0.58)
            ankle = PosePoint(x: 0.9, y: 0.8)
        }
        let base = knees ? knee : ankle
        let hip = PosePoint(x: 0.5, y: shoulder.y + (base.y - shoulder.y) * (0.2 / (base.x - 0.3)) + sag)
        return [.leftShoulder: shoulder, .leftElbow: elbow, .leftWrist: wrist, .leftHip: hip, .leftKnee: knee, .leftAnkle: ankle]
    }

    /// 선 채로 팔만 굽히기.
    static func standingArmCurl(_ elbowDeg: Double) -> [Landmark: PosePoint] {
        let shoulder = PosePoint(x: 0.5, y: 0.3)
        let elbow = PosePoint(x: 0.5, y: 0.45)
        let t = elbowDeg * .pi / 180
        let wrist = PosePoint(x: elbow.x + 0.15 * sin(t), y: elbow.y - 0.15 * cos(t))
        return [.leftShoulder: shoulder, .leftElbow: elbow, .leftWrist: wrist,
                .leftHip: PosePoint(x: 0.5, y: 0.6), .leftKnee: PosePoint(x: 0.5, y: 0.75), .leftAnkle: PosePoint(x: 0.5, y: 0.9)]
    }

    /// 정면 팔 올리기. 각도는 엉덩이-어깨-손목 (0 = 내림, 180 = 머리 위).
    static func armRaise(_ leftDeg: Double, _ rightDeg: Double? = nil) -> [Landmark: PosePoint] {
        func arm(_ shoulder: PosePoint, _ deg: Double, _ dir: Double) -> PosePoint {
            let t = deg * .pi / 180
            return PosePoint(x: shoulder.x + dir * 0.25 * sin(t), y: shoulder.y + 0.25 * cos(t))
        }
        let ls = PosePoint(x: 0.4, y: 0.3)
        let rs = PosePoint(x: 0.6, y: 0.3)
        return [.leftShoulder: ls, .rightShoulder: rs,
                .leftHip: PosePoint(x: 0.4, y: 0.6), .rightHip: PosePoint(x: 0.6, y: 0.6),
                .leftWrist: arm(ls, leftDeg, -1), .rightWrist: arm(rs, rightDeg ?? leftDeg, 1)]
    }
}

/// 30fps로 각도를 움직이며 프레임을 흘려 넣는다.
final class Driver {
    let counter: RepCounter
    let pose: (Double) -> [Landmark: PosePoint]
    var t: Int64 = 0
    var last: RepState?

    init(_ counter: RepCounter, pose: @escaping (Double) -> [Landmark: PosePoint]) {
        self.counter = counter
        self.pose = pose
    }

    func hold(_ deg: Double, _ ms: Int64) { step(deg, deg, ms) }

    func step(_ from: Double, _ to: Double, _ ms: Int64) {
        let frames = max(ms / 33, 1)
        for i in 1...frames {
            let deg = from + (to - from) * Double(i) / Double(frames)
            t += 33
            last = counter.update(PoseFrame(timestampMs: t, points: pose(deg)))
        }
    }

    func rep(_ top: Double, _ bottom: Double, ms: Int64 = 1500) {
        step(top, bottom, ms / 2)
        step(bottom, top, ms / 2)
    }

    func raw(_ points: [Landmark: PosePoint], _ ms: Int64) {
        for _ in 0..<(ms / 33) {
            t += 33
            last = counter.update(PoseFrame(timestampMs: t, points: points))
        }
    }

    var reps: Int { last!.reps }
}
