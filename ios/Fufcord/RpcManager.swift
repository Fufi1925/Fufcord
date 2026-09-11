/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation
import UIKit
import Combine

/// Hält die RPC-Verbindung: Gateway + Reconnect + Watchdog + Hintergrund-Aufgaben.
/// (Port von Androids RpcService — ohne Foreground-Service, dafür Audio-KeepAlive.)
class RpcManager: ObservableObject {
    static let shared = RpcManager()

    @Published var isRunning = false
    @Published var statusText = "Gestoppt"
    @Published var activityName = ""

    private var gw: Gateway?
    private var backoff = 5
    private var reconnectWork: DispatchWorkItem?
    private var watchdog: Timer?
    private var startTs: Int64 = 0
    private var bgTask: UIBackgroundTaskIdentifier = .invalid

    func buildPresence() -> [String: Any] {
        let store = Store.shared
        let a = store.act
        DispatchQueue.main.async { self.activityName = a.name }
        return [
            "since": Int64(Date().timeIntervalSince1970 * 1000),
            "activities": [a.toPresence(appId: store.appId, safe: store.safeMode, startTs: startTs)],
            "status": a.status,
            "afk": false
        ]
    }

    func start() {
        let store = Store.shared
        if store.token.isEmpty {
            setStatus("Kein Token! In der App eintragen.")
            return
        }
        endBg()
        bgTask = UIApplication.shared.beginBackgroundTask(withName: "fufcord-rpc") { [weak self] in
            self?.endBg()
        }
        startTs = Int64(Date().timeIntervalSince1970 * 1000)
        setRunning(true)
        if store.bgAudio { AudioKeepAlive.shared.start() }
        connect()
        startWatchdog()
    }

    func stop() {
        cancelReconnect()
        stopWatchdog()
        gw?.disconnect()
        gw = nil
        AudioKeepAlive.shared.stop()
        setRunning(false)
        setStatus("Gestoppt")
        endBg()
    }

    /// Aktuelle Activity sofort an Discord schicken.
    func refresh() {
        gw?.sendPresence(buildPresence())
        setStatus("✅ Online — aktualisiert • \(activityName)")
    }

    private func connect() {
        cancelReconnect()
        gw?.disconnect()
        gw = nil
        setStatus("Verbinde...")
        let g = Gateway()
        gw = g
        g.onReady = { [weak self] name in
            guard let self = self else { return }
            self.backoff = 5
            self.gw?.sendPresence(self.buildPresence())
            self.setStatus("Online als \(name)")
        }
        g.onClosed = { [weak self] _ in self?.scheduleReconnect("Verbindung geschlossen") }
        g.onFailure = { [weak self] m in self?.scheduleReconnect(m) }
        Task {
            let (ok, _) = await DiscordApi.getMe(Store.shared.token)
            if !ok {
                DispatchQueue.main.async { [weak self] in
                    self?.setStatus("❌ Token ungültig!")
                    self?.stop()
                }
                return
            }
            let p = self.buildPresence()
            g.connect(token: Store.shared.token, presence: p)
        }
    }

    private func scheduleReconnect(_ reason: String) {
        if !isRunning { return }
        cancelReconnect()
        gw?.disconnect()
        setStatus("Neu verbinden (\(backoff)s): \(reason)")
        let wait = backoff
        backoff = min(backoff * 2, 300)
        let work = DispatchWorkItem { [weak self] in self?.connect() }
        reconnectWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(wait), execute: work)
    }

    private func cancelReconnect() {
        reconnectWork?.cancel()
        reconnectWork = nil
    }

    private func startWatchdog() {
        stopWatchdog()
        watchdog = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let silent = Int64(Date().timeIntervalSince1970 * 1000) - Gateway.lastMessageMs
            if self.isRunning && Gateway.lastMessageMs > 0 && silent > 150000 {
                self.gw?.disconnect()
                self.connect()
            }
        }
    }

    private func stopWatchdog() {
        watchdog?.invalidate()
        watchdog = nil
    }

    private func endBg() {
        if bgTask != .invalid {
            UIApplication.shared.endBackgroundTask(bgTask)
            bgTask = .invalid
        }
    }

    private func setStatus(_ s: String) {
        DispatchQueue.main.async { self.statusText = s }
    }

    private func setRunning(_ r: Bool) {
        DispatchQueue.main.async { self.isRunning = r }
    }
}
