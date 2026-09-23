// swift-tools-version:5.9
import PackageDescription

// 플랫폼 독립 로직: 반복 카운터, 미션 진행, 알람 모델, 기록 통계.
// Android의 core/pose, core/alarm과 같은 규칙을 Swift로 옮긴 것이다.
let package = Package(
    name: "FitWakeCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FitWakeCore", targets: ["FitWakeCore"])],
    targets: [
        .target(name: "FitWakeCore"),
        .testTarget(name: "FitWakeCoreTests", dependencies: ["FitWakeCore"]),
    ]
)
