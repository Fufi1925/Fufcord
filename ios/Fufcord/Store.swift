/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation
import Combine

/// Zentraler Speicher (1:1-Keys wie Android) + Presets.
class Store: ObservableObject {
    static let shared = Store()

    @Published var token = "" { didSet { d.set(token.trimmingCharacters(in: .whitespaces), forKey: "token") } }
    @Published var appId = "" { didSet { d.set(appId.trimmingCharacters(in: .whitespaces), forKey: "app_id") } }
    @Published var safeMode = false { didSet { d.set(safeMode, forKey: "safe_mode") } }
    @Published var bgAudio = true { didSet { d.set(bgAudio, forKey: "bg_audio") } }
    @Published var setupDone = false { didSet { d.set(setupDone, forKey: "setup_done") } }
    @Published var onboardingDone = false { didSet { d.set(onboardingDone, forKey: "onboarding_done") } }
    @Published var skipVersion = "" { didSet { d.set(skipVersion, forKey: "skip_version") } }
    @Published var discordPromoSeen = false { didSet { d.set(discordPromoSeen, forKey: "discord_promo_seen") } }
    @Published var tokenAsked = false { didSet { d.set(tokenAsked, forKey: "token_asked") } }
    @Published var customAssets: Set<String> = [] { didSet { d.set(Array(customAssets), forKey: "custom_assets") } }
    @Published var act = ActConfig()

    private let d = UserDefaults.standard

    init() {
        load()
    }

    func load() {
        token = d.string(forKey: "token") ?? ""
        appId = d.string(forKey: "app_id") ?? ""
        safeMode = d.bool(forKey: "safe_mode")
        bgAudio = d.object(forKey: "bg_audio") == nil ? true : d.bool(forKey: "bg_audio")
        setupDone = d.bool(forKey: "setup_done")
        onboardingDone = d.bool(forKey: "onboarding_done")
        skipVersion = d.string(forKey: "skip_version") ?? ""
        discordPromoSeen = d.bool(forKey: "discord_promo_seen")
        tokenAsked = d.bool(forKey: "token_asked")
        customAssets = Set(d.stringArray(forKey: "custom_assets") ?? [])
        if let raw = d.string(forKey: "activity"), !raw.isEmpty, let dict = jsonDict(raw) {
            act = ActConfig.sanitize(dict).0
        } else {
            act = ActConfig(details: "Custom Rich Presence wie Vencord", state: "läuft auf iPhone 📱")
        }
    }

    func saveAct() {
        d.set(jsonString(act.toDict()), forKey: "activity")
    }

    /// Alles löschen (Gefahrenzone): Einstellungen, Token, Presets.
    func clearAll() {
        for k in ["token", "app_id", "safe_mode", "bg_audio", "setup_done", "onboarding_done",
                  "skip_version", "discord_promo_seen", "token_asked", "custom_assets", "activity"] {
            d.removeObject(forKey: k)
        }
        try? FileManager.default.removeItem(at: presetDir())
        load()
    }

    // MARK: - Presets

    func bundledPresets() -> [Preset] {
        guard let url = Bundle.main.url(forResource: "bundled_presets", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let arr = o["presets"] as? [[String: Any]] else { return [] }
        var out: [Preset] = []
        for p in arr {
            var act = ActConfig.sanitize(p["activity"] as? [String: Any]).0
            let st = s(p["status"], "online").lowercased()
            act.status = ActConfig.STATUS[st] != nil ? st : "online"
            out.append(Preset(file: s(p["file"]), title: s(p["title"]), status: st,
                              appId: "", act: act, icon: s(p["icon"])))
        }
        return out
    }

    private func presetDir() -> URL {
        let u = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("presets")
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    func customPresets() -> [Preset] {
        var out: [Preset] = []
        let files = (try? FileManager.default.contentsOfDirectory(
            at: presetDir(), includingPropertiesForKeys: nil)) ?? []
        for f in files where f.pathExtension == "json" {
            guard let data = try? Data(contentsOf: f),
                  let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }
            var act = ActConfig.sanitize(o["activity"] as? [String: Any]).0
            let st = s(o["status"], "online").lowercased()
            act.status = ActConfig.STATUS[st] != nil ? st : "online"
            let base = f.deletingPathExtension().lastPathComponent
            out.append(Preset(file: base, title: base, status: st,
                              appId: s(o["application_id"]), act: act, icon: ""))
        }
        return out
    }

    func saveCustomPreset(name: String, status: String, appId: String, act: ActConfig) {
        let o: [String: Any] = ["status": status, "application_id": appId, "activity": act.toDict()]
        if let data = try? JSONSerialization.data(withJSONObject: o) {
            try? data.write(to: presetDir().appendingPathComponent("\(name).json"))
        }
    }

    func deleteCustomPreset(name: String) {
        try? FileManager.default.removeItem(at: presetDir().appendingPathComponent("\(name).json"))
    }
}
