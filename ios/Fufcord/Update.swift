/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation

/// Update-Check: meldet sich nur, wenn das Release eine .ipa enthält.
enum UpdateCheck {

    static func current() -> String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.14"
    }

    static func check() async -> (tag: String, notes: String, url: String)? {
        guard let u = URL(string: "https://api.github.com/repos/Fufi1925/Fufcord/releases/latest") else {
            return nil
        }
        var r = URLRequest(url: u)
        r.setValue("Fufcord-iOS", forHTTPHeaderField: "User-Agent")
        r.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        guard let (data, _) = try? await URLSession.shared.data(for: r),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        var tag = s(o["tag_name"]).trimmingCharacters(in: .whitespaces)
        if tag.hasPrefix("v") { tag = String(tag.dropFirst()) }
        if tag.isEmpty || tag.hasPrefix("4.") { return nil }
        if !isNewer(tag, current()) { return nil }
        if Store.shared.skipVersion == tag { return nil }
        var ipa = ""
        if let assets = o["assets"] as? [[String: Any]] {
            for a in assets {
                let du = s(a["browser_download_url"])
                if du.hasSuffix(".ipa") { ipa = du; break }
            }
        }
        if ipa.isEmpty { return nil } // kein iOS-Build dabei → ruhig bleiben
        let notes = String(s(o["body"]).prefix(1200))
        return (tag, notes, ipa)
    }

    static func isNewer(_ remote: String, _ cur: String) -> Bool {
        let rp = remote.split(separator: ".").map { Int($0) ?? 0 }
        let cp = cur.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(rp.count, cp.count) {
            let a = i < rp.count ? rp[i] : 0
            let b = i < cp.count ? cp[i] : 0
            if a != b { return a > b }
        }
        return false
    }
}
