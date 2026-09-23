import AVFoundation

/// 앱 안에서 울리는 알람음 (삐-삐 반복). 무음 스위치와 상관없이 들린다.
final class TonePlayer {
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()

    init() {
        let format = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1)!
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: format)
        guard let buffer = Self.beepBuffer(format: format) else { return }
        do {
            try engine.start()
            player.scheduleBuffer(buffer, at: nil, options: .loops)
        } catch {
            print("TonePlayer: \(error)")
        }
    }

    func play() { player.play() }

    func stop() {
        player.stop()
        engine.stop()
    }

    var volume: Float {
        get { player.volume }
        set { player.volume = newValue }
    }

    /// 0.4초 880Hz + 0.6초 무음.
    private static func beepBuffer(format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let rate = format.sampleRate
        let frames = AVAudioFrameCount(rate)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames),
              let data = buffer.floatChannelData?[0] else { return nil }
        buffer.frameLength = frames
        let beepFrames = Int(rate * 0.4)
        for i in 0..<Int(frames) {
            data[i] = i < beepFrames ? Float(sin(2 * .pi * 880 * Double(i) / rate)) * 0.8 : 0
        }
        return buffer
    }
}
