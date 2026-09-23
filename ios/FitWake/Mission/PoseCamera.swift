import AVFoundation
import FitWakeCore
import UIKit
import Vision

/// 카메라 프레임마다 Apple Vision으로 몸의 관절을 찾는다. 영상은 저장하거나 전송하지 않는다.
final class PoseCamera: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    struct Result {
        /// 판정용 좌표(픽셀, y 아래로 증가)와 신뢰도.
        var frame: PoseFrame
        /// 화면 표시용 정규화 좌표(0...1, y 아래로 증가).
        var normalized: [Landmark: CGPoint]
        /// 세로로 돌린 이미지 크기.
        var imageSize: CGSize
    }

    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "fitwake.pose")
    private let output = AVCaptureVideoDataOutput()
    private let request = VNDetectHumanBodyPoseRequest()
    private(set) var position: AVCaptureDevice.Position = .front

    /// 메인 스레드에서 호출된다.
    var onResult: ((Result) -> Void)?

    private static let joints: [VNHumanBodyPoseObservation.JointName: Landmark] = [
        .leftShoulder: .leftShoulder, .rightShoulder: .rightShoulder,
        .leftElbow: .leftElbow, .rightElbow: .rightElbow,
        .leftWrist: .leftWrist, .rightWrist: .rightWrist,
        .leftHip: .leftHip, .rightHip: .rightHip,
        .leftKnee: .leftKnee, .rightKnee: .rightKnee,
        .leftAnkle: .leftAnkle, .rightAnkle: .rightAnkle,
    ]

    func configure(position: AVCaptureDevice.Position) {
        self.position = position
        session.beginConfiguration()
        session.sessionPreset = .hd1280x720
        session.inputs.forEach { session.removeInput($0) }
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
           let input = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(input) {
            session.addInput(input)
        }
        if !session.outputs.contains(output), session.canAddOutput(output) {
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(self, queue: queue)
            session.addOutput(output)
        }
        if let connection = output.connection(with: .video) {
            // 세로 화면 기준으로 돌려서 받는다. 전면 카메라는 미리보기처럼 좌우 반전.
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = position == .front
            }
        }
        session.commitConfiguration()
    }

    func start() {
        queue.async { [session] in
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        queue.async { [session] in
            if session.isRunning { session.stopRunning() }
        }
    }

    func setTorch(_ on: Bool) {
        guard let device = (session.inputs.first as? AVCaptureDeviceInput)?.device,
              device.hasTorch, (try? device.lockForConfiguration()) != nil else { return }
        device.torchMode = on ? .on : .off
        device.unlockForConfiguration()
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let size = CGSize(width: CVPixelBufferGetWidth(pixelBuffer), height: CVPixelBufferGetHeight(pixelBuffer))
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up)
        try? handler.perform([request])

        var points: [Landmark: PosePoint] = [:]
        var normalized: [Landmark: CGPoint] = [:]
        if let observation = request.results?.first,
           let recognized = try? observation.recognizedPoints(.all) {
            for (joint, landmark) in Self.joints {
                guard let p = recognized[joint], p.confidence > 0 else { continue }
                // Vision 좌표는 왼쪽 아래가 원점이다.
                let nx = p.location.x
                let ny = 1 - p.location.y
                points[landmark] = PosePoint(x: nx * size.width, y: ny * size.height, confidence: Double(p.confidence))
                normalized[landmark] = CGPoint(x: nx, y: ny)
            }
        }
        DispatchQueue.main.async { [weak self] in
            let frame = PoseFrame(timestampMs: Int64(CACurrentMediaTime() * 1000), points: points)
            self?.onResult?(Result(frame: frame, normalized: normalized, imageSize: size))
        }
    }
}
