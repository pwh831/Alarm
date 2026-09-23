import Foundation

/// 운동 판정에 쓰는 관절. 좌표계는 이미지 기준(y가 아래로 증가)이면 단위는 상관없다.
public enum Landmark: CaseIterable, Hashable, Sendable {
    case leftShoulder, rightShoulder
    case leftElbow, rightElbow
    case leftWrist, rightWrist
    case leftHip, rightHip
    case leftKnee, rightKnee
    case leftAnkle, rightAnkle
}

public struct PosePoint: Equatable, Sendable {
    public var x: Double
    public var y: Double
    public var confidence: Double

    public init(x: Double, y: Double, confidence: Double = 1) {
        self.x = x
        self.y = y
        self.confidence = confidence
    }
}

public struct PoseFrame: Sendable {
    public var timestampMs: Int64
    public var points: [Landmark: PosePoint]

    public init(timestampMs: Int64, points: [Landmark: PosePoint]) {
        self.timestampMs = timestampMs
        self.points = points
    }
}

public enum Side: CaseIterable, Sendable {
    case left, right

    public var shoulder: Landmark { self == .left ? .leftShoulder : .rightShoulder }
    public var elbow: Landmark { self == .left ? .leftElbow : .rightElbow }
    public var wrist: Landmark { self == .left ? .leftWrist : .rightWrist }
    public var hip: Landmark { self == .left ? .leftHip : .rightHip }
    public var knee: Landmark { self == .left ? .leftKnee : .rightKnee }
    public var ankle: Landmark { self == .left ? .leftAnkle : .rightAnkle }
}

/// b를 꼭짓점으로 하는 각 abc (0...180도).
public func angleDeg(_ a: PosePoint, _ b: PosePoint, _ c: PosePoint) -> Double {
    let v1 = atan2(a.y - b.y, a.x - b.x)
    let v2 = atan2(c.y - b.y, c.x - b.x)
    var deg = abs((v1 - v2) * 180 / .pi)
    if deg > 180 { deg = 360 - deg }
    return deg
}

public func distance(_ a: PosePoint, _ b: PosePoint) -> Double {
    hypot(a.x - b.x, a.y - b.y)
}

extension PoseFrame {
    /// 주어진 관절이 모두 minConfidence 이상이면 해당 좌표들을, 아니면 nil.
    func visible(_ minConfidence: Double, _ landmarks: Landmark...) -> [PosePoint]? {
        var result: [PosePoint] = []
        for l in landmarks {
            guard let p = points[l], p.confidence >= minConfidence else { return nil }
            result.append(p)
        }
        return result
    }
}
