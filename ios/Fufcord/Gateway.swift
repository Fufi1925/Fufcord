/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation

/// Discord Gateway (WebSocket): Identify, Heartbeat, Presence. 1:1-Port von Android.
class Gateway {
    static var lastMessageMs: Int64 = 0

    var onReady: ((String) -> Void)?
    var onClosed: ((Int) -> Void)?
    var onFailure: ((String) -> Void)?

    private var task: URLSessionWebSocketTask?
    private var seq: Int?
    private var closed = false
    private var rxTask: Task<Void, Never>?
    private var hbTask: Task<Void, Never>?

    func connect(token: String, presence: [String: Any]) {
        disconnect()
        closed = false
        seq = nil
        guard let url = URL(string: "wss://gateway.discord.gg/?v=10&encoding=json") else { return }
        var req = URLRequest(url: url)
        req.setValue(DiscordApi.UA, forHTTPHeaderField: "User-Agent")
        let t = URLSession.shared.webSocketTask(with: req)
        task = t
        rxTask = Task { [weak self] in
            guard let self = self else { return }
            while !self.closed && !Task.isCancelled {
                do {
                    let m = try await t.receive()
                    switch m {
                    case .string(let str):
                        self.handle(str, token: token, presence: presence)
                    case .data(let d):
                        if let str = String(data: d, encoding: .utf8) {
                            self.handle(str, token: token, presence: presence)
                        }
                    @unknown default:
                        break
                    }
                } catch {
                    if !self.closed { self.fail("Verbindung verloren") }
                    break
                }
            }
        }
        t.resume()
    }

    private func handle(_ text: String, token: String, presence: [String: Any]) {
        Gateway.lastMessageMs = Int64(Date().timeIntervalSince1970 * 1000)
        guard let data = text.data(using: .utf8),
              let msg = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        if let sq = msg["s"] as? Int { seq = sq }
        let op = msg["op"] as? Int ?? -1
        if op == 10 { // Hello
            let d = msg["d"] as? [String: Any]
            let interval = d?["heartbeat_interval"] as? Int64 ?? 41250
            startHeartbeat(interval)
            let id: [String: Any] = [
                "token": token,
                "intents": 0,
                "properties": ["os": "iOS", "browser": "Discord iOS", "device": "iOS"],
                "presence": presence
            ]
            send(["op": 2, "d": id])
        } else if op == 0 { // Dispatch
            if (msg["t"] as? String) == "READY" {
                let d = msg["d"] as? [String: Any]
                let u = d?["user"] as? [String: Any]
                let name = u?["username"] as? String ?? "?"
                DispatchQueue.main.async { self.onReady?(name) }
            }
        } else if op == 7 || op == 9 { // Reconnect / Invalid Session
            closeAndReconnect()
        }
    }

    func sendPresence(_ presence: [String: Any]) {
        send(["op": 3, "d": presence])
    }

    private func send(_ obj: [String: Any]) {
        guard let d = try? JSONSerialization.data(withJSONObject: obj),
              let str = String(data: d, encoding: .utf8) else { return }
        task?.send(.string(str)) { _ in }
    }

    private func startHeartbeat(_ ms: Int64) {
        hbTask?.cancel()
        hbTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(max(1000, ms)) * 1_000_000)
                if Task.isCancelled { break }
                guard let self = self else { break }
                var o: [String: Any] = ["op": 1]
                if let sq = self.seq {
                    o["d"] = sq
                } else {
                    o["d"] = NSNull()
                }
                self.send(o)
            }
        }
    }

    private func closeAndReconnect() {
        closed = true
        hbTask?.cancel()
        rxTask?.cancel()
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        DispatchQueue.main.async { self.onClosed?(4000) }
    }

    private func fail(_ m: String) {
        hbTask?.cancel()
        DispatchQueue.main.async { self.onFailure?(m) }
    }

    func disconnect() {
        closed = true
        hbTask?.cancel()
        rxTask?.cancel()
        hbTask = nil
        rxTask = nil
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        onReady = nil
        onClosed = nil
        onFailure = nil
    }
}
