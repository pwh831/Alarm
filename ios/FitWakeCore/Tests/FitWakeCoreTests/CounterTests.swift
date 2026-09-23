import XCTest
@testable import FitWakeCore

final class GeometryTests: XCTestCase {
    func testAngles() {
        XCTAssertEqual(angleDeg(PosePoint(x: 0, y: 1), PosePoint(x: 0, y: 0), PosePoint(x: 1, y: 0)), 90, accuracy: 1e-6)
        XCTAssertEqual(angleDeg(PosePoint(x: -1, y: 0), PosePoint(x: 0, y: 0), PosePoint(x: 1, y: 0)), 180, accuracy: 1e-6)
        XCTAssertEqual(angleDeg(PosePoint(x: 1, y: 0), PosePoint(x: 0, y: 0), PosePoint(x: 1, y: -1)), 45, accuracy: 1e-6)
    }

    func testSyntheticPoses() {
        let sq = Poses.squat(95)
        XCTAssertEqual(angleDeg(sq[.leftHip]!, sq[.leftKnee]!, sq[.leftAnkle]!), 95, accuracy: 1e-3)
        let pu = Poses.pushup(80)
        XCTAssertEqual(angleDeg(pu[.leftShoulder]!, pu[.leftElbow]!, pu[.leftWrist]!), 80, accuracy: 1e-3)
    }
}

final class SquatCounterTests: XCTestCase {
    func testCountsFullSquats() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.squat($0) }
        d.hold(175, 500)
        for _ in 0..<5 { d.rep(175, 85) }
        XCTAssertEqual(d.reps, 5)
    }

    func testShallowSquatDependsOnDifficulty() {
        for (difficulty, expected) in [(Difficulty.easy, 3), (.normal, 0), (.hard, 0)] {
            let d = Driver(SquatCounter(difficulty: difficulty)) { Poses.squat($0) }
            d.hold(175, 500)
            for _ in 0..<3 { d.rep(175, 110) }
            XCTAssertEqual(d.reps, expected, "\(difficulty)")
        }
    }

    func testTooFastRepIsRejected() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.squat($0) }
        d.hold(175, 500)
        d.step(175, 85, 100)
        d.hold(85, 100)
        d.step(85, 175, 100)
        d.hold(175, 33)
        XCTAssertEqual(d.reps, 0)
        XCTAssertEqual(d.last!.hint, .tooFast)
        d.rep(175, 85)
        XCTAssertEqual(d.reps, 1)
    }

    func testMustStartFromStanding() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.squat($0) }
        d.hold(85, 500)
        d.step(85, 175, 700)
        XCTAssertEqual(d.reps, 0)
    }

    func testKneeOnlyBendDoesNotCount() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.kneeOnlyBend($0) }
        d.hold(178, 500)
        for _ in 0..<3 { d.rep(178, 70) }
        XCTAssertEqual(d.reps, 0)
    }

    func testLowConfidenceIgnored() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.squat($0, confidence: 0.2) }
        for _ in 0..<3 { d.rep(175, 85) }
        XCTAssertEqual(d.reps, 0)
        XCTAssertEqual(d.last!.hint, .bodyNotVisible)
    }

    func testBriefOcclusionKeepsRep() {
        let d = Driver(SquatCounter(difficulty: .normal)) { Poses.squat($0) }
        d.hold(175, 500)
        d.step(175, 85, 700)
        d.raw([:], 300)
        d.step(85, 175, 700)
        XCTAssertEqual(d.reps, 1)
    }
}

final class PushupCounterTests: XCTestCase {
    func testCountsPlankPushups() {
        let d = Driver(PushupCounter(difficulty: .normal)) { Poses.pushup($0) }
        d.hold(175, 500)
        for _ in 0..<4 { d.rep(175, 80) }
        XCTAssertEqual(d.reps, 4)
    }

    func testStandingArmCurlDoesNotCount() {
        let d = Driver(PushupCounter(difficulty: .easy)) { Poses.standingArmCurl($0) }
        d.hold(175, 500)
        for _ in 0..<3 { d.rep(175, 60) }
        XCTAssertEqual(d.reps, 0)
        XCTAssertEqual(d.last!.hint, .getHorizontal)
    }

    func testKneePushupOnlyOnEasy() {
        let easy = Driver(PushupCounter(difficulty: .easy)) { Poses.pushup($0, knees: true) }
        easy.hold(175, 500)
        for _ in 0..<2 { easy.rep(175, 90) }
        XCTAssertEqual(easy.reps, 2)

        let normal = Driver(PushupCounter(difficulty: .normal)) { Poses.pushup($0, knees: true) }
        normal.hold(175, 500)
        for _ in 0..<2 { normal.rep(175, 80) }
        XCTAssertEqual(normal.reps, 0)
        XCTAssertEqual(normal.last!.hint, .keepBodyStraight)
    }
}

final class ArmRaiseCounterTests: XCTestCase {
    func testCountsFullRaisesButNotOneArm() {
        let both = Driver(ArmRaiseCounter(difficulty: .normal)) { Poses.armRaise(180 - $0) }
        both.hold(170, 500)
        for _ in 0..<4 { both.rep(170, 10) }
        XCTAssertEqual(both.reps, 4)

        let oneArm = Driver(ArmRaiseCounter(difficulty: .normal)) { Poses.armRaise(180 - $0, 10) }
        oneArm.hold(170, 500)
        for _ in 0..<3 { oneArm.rep(170, 10) }
        XCTAssertEqual(oneArm.reps, 0)
    }
}

final class MissionSessionTests: XCTestCase {
    func testCountdownThenCountsAndPausesWhilePhoneMoves() {
        let s = MissionSession(counter: SquatCounter(difficulty: .normal))
        var t: Int64 = 0
        func feed(_ deg: Double, _ ms: Int64, moving: Bool = false) -> MissionSession.State {
            var state: MissionSession.State!
            for _ in 0..<max(ms / 33, 1) {
                t += 33
                state = s.update(PoseFrame(timestampMs: t, points: Poses.squat(deg)), phoneMoving: moving)
            }
            return state
        }
        XCTAssertEqual(feed(175, 33).countdownSec, 3)
        XCTAssertEqual(feed(175, 100, moving: true).rep.hint, .phoneMoving)
        XCTAssertEqual(feed(175, 33).countdownSec, 3)
        XCTAssertTrue(feed(175, 3100).started)

        for deg in [120.0, 80, 80, 120, 175] { _ = feed(deg, 150, moving: true) }
        XCTAssertEqual(feed(175, 100).rep.reps, 0)

        for deg in [150.0, 120, 90, 80, 80, 80, 100, 130, 160, 175, 175, 175] { _ = feed(deg, 100) }
        XCTAssertEqual(feed(175, 100).rep.reps, 1)
    }
}

final class MotionGuardTests: XCTestCase {
    func testStillVsShaking() {
        let guardian = MotionGuard()
        var rng = SystemRandomNumberGenerator()
        var t: Int64 = 0
        for _ in 0..<250 {
            t += 20
            guardian.onAccelerometer(timestampMs: t, x: Double.random(in: -0.3...0.3, using: &rng), y: 9.81, z: 0)
        }
        XCTAssertFalse(guardian.isMoving(nowMs: t))
        for _ in 0..<25 {
            t += 20
            guardian.onAccelerometer(timestampMs: t, x: 4 * sin(Double(t) / 30), y: 9.81, z: 0)
        }
        XCTAssertTrue(guardian.isMoving(nowMs: t))
        for _ in 0..<75 {
            t += 20
            guardian.onAccelerometer(timestampMs: t, x: 0, y: 9.81, z: 0)
        }
        XCTAssertFalse(guardian.isMoving(nowMs: t))
    }
}
