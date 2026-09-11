/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation
import AVFAudio

/// Hält die App im Hintergrund am Leben: spielt lautloses Audio in Dauerschleife
/// (iOS-Hintergrundmodus "audio"). Verbrauch: praktisch null, 100 % lautlos.
class AudioKeepAlive {
    static let shared = AudioKeepAlive()

    private var player: AVAudioPlayer?

    func start() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { }
        if player == nil {
            player = try? AVAudioPlayer(data: silentWav())
        }
        player?.numberOfLoops = -1
        player?.volume = 0
        player?.play()
    }

    func stop() {
        player?.stop()
        player = nil
        try? AVAudioSession.sharedInstance().setActive(false)
    }

    /// Baut 1 Sekunde Stille (8000 Hz, 16-bit PCM) als WAV im Speicher.
    private func silentWav() -> Data {
        let rate = 8000
        let samples = rate * 1
        var d = Data()
        func u32(_ v: Int) {
            var x = UInt32(v).littleEndian
            d.append(Data(bytes: &x, count: 4))
        }
        func u16(_ v: Int) {
            var x = UInt16(v).littleEndian
            d.append(Data(bytes: &x, count: 2))
        }
        d.append("RIFF".data(using: .ascii)!)
        u32(36 + samples * 2)
        d.append("WAVE".data(using: .ascii)!)
        d.append("fmt ".data(using: .ascii)!)
        u32(16)
        u16(1)
        u16(1)
        u32(rate)
        u32(rate * 2)
        u16(2)
        u16(16)
        d.append("data".data(using: .ascii)!)
        u32(samples * 2)
        d.append(Data(count: samples * 2))
        return d
    }
}
