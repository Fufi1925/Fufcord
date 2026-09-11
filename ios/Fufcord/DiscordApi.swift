/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation

/// Direkte Discord-API calls (async — 1:1-Port von Android).
enum DiscordApi {

    static let BASE = "https://discord.com/api/v9"
    static let UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    static func req(_ path: String, token: String, method: String = "GET",
                    json: [String: Any]? = nil) async -> (Int, String) {
        guard let url = URL(string: BASE + path) else { return (0, "URL-Fehler") }
        var r = URLRequest(url: url)
        r.httpMethod = method
        r.timeoutInterval = 25
        r.setValue(token, forHTTPHeaderField: "Authorization")
        r.setValue(UA, forHTTPHeaderField: "User-Agent")
        if let j = json {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try? JSONSerialization.data(withJSONObject: j)
        }
        do {
            let (data, resp) = try await URLSession.shared.data(for: r)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            return (code, String(data: data, encoding: .utf8) ?? "")
        } catch {
            return (0, error.localizedDescription)
        }
    }

    /// Token gültig? → (ok, username_oder_fehler)
    static func getMe(_ token: String) async -> (Bool, String) {
        let (code, body) = await req("/users/@me", token: token)
        if code == 200 {
            return (true, jsonDict(body).map { s($0["username"], "?") } ?? "?")
        }
        return (false, "HTTP \(code)")
    }

    struct DUser {
        var ok: Bool
        var name: String
        var handle: String
        var avatarUrl: String
    }

    /// Fremden User per ID laden (für Credits).
    static func getUser(token: String, userId: String) async -> DUser {
        let bad = DUser(ok: false, name: "", handle: "", avatarUrl: "")
        let (code, body) = await req("/users/\(userId)", token: token)
        if code != 200 { return bad }
        guard let o = jsonDict(body) else { return bad }
        let username = s(o["username"], "?")
        var global = s(o["global_name"])
        if global.isEmpty || global == "null" { global = username }
        let av = s(o["avatar"])
        let url: String
        if !av.isEmpty && av != "null" {
            url = "https://cdn.discordapp.com/avatars/\(userId)/\(av).png?size=128"
        } else {
            let idx = Int((Int64(userId) ?? 0) >> 22) % 6
            url = "https://cdn.discordapp.com/embed/avatars/\(idx).png"
        }
        return DUser(ok: true, name: global, handle: "@\(username)", avatarUrl: url)
    }

    /// Eigene Apps → (ok, [(id, name)])
    static func listApps(token: String) async -> (Bool, [(String, String)]) {
        let (code, body) = await req("/applications?with_team_applications=true", token: token)
        if code != 200 { return (false, []) }
        guard let data = body.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return (false, [])
        }
        return (true, arr.map { (s($0["id"]), s($0["name"], "?")) })
    }

    /// Neue Discord-App anlegen (wie im Portal) → (ok, appId_oder_fehler).
    static func createApp(token: String, name: String) async -> (Bool, String) {
        let clean = String(name.trimmingCharacters(in: .whitespaces).prefix(100))
        if clean.count < 2 { return (false, "Name zu kurz") }
        let (code, body) = await req("/applications", token: token, method: "POST", json: ["name": clean])
        if code >= 200 && code <= 299 {
            let id = jsonDict(body).map { s($0["id"]) } ?? ""
            if !id.isEmpty { return (true, id) }
            return (false, "Keine ID zurück")
        }
        return (false, "HTTP \(code): \(String(body.prefix(300)))")
    }

    /// Assets einer App → (ok, [(assetId, name)])
    static func listAssets(token: String, appId: String) async -> (Bool, [(String, String)]) {
        let (code, body) = await req("/oauth2/applications/\(appId)/assets?nocache=true", token: token)
        if code != 200 { return (false, []) }
        guard let data = body.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return (false, [])
        }
        return (true, arr.map { (s($0["id"]), s($0["name"], "?")) })
    }

    /// Bild hochladen → (ok, assetId_oder_fehler). imageDataUrl = data:image/png;base64,...
    /// Endpunkt + Format exakt wie das Discord-Developer-Portal.
    static func uploadAsset(token: String, appId: String, name: String,
                            imageDataUrl: String) async -> (Bool, String) {
        let cleanName = name.trimmingCharacters(in: .whitespaces).lowercased()
        let cleanImg = imageDataUrl.components(separatedBy: .whitespacesAndNewlines).joined()
        if cleanName.range(of: "^[a-z0-9_]{1,32}$", options: .regularExpression) == nil {
            return (false, "Ungültiger Asset-Name: '\(cleanName)'")
        }
        // Gleichnamiges Asset erst löschen (Update = ersetzen, sonst 400).
        let (okL, assets) = await listAssets(token: token, appId: appId)
        if okL, let hit = assets.first(where: { $0.1 == cleanName }) {
            _ = await deleteAsset(token: token, appId: appId, assetId: hit.0)
        }
        var last: (Bool, String) = (false, "Unbekannter Fehler")
        for asString in [true, false] {
            var js: [String: Any] = ["name": cleanName, "image": cleanImg]
            js["type"] = asString ? "1" : 1
            let (code, txt) = await req("/oauth2/applications/\(appId)/assets",
                                        token: token, method: "POST", json: js)
            if code >= 200 && code <= 299 {
                let id = jsonDict(txt).map { s($0["id"], "?") } ?? "?"
                return (true, id)
            }
            last = (false, "HTTP \(code): \(String(txt.prefix(500)))")
            if code != 400 { return last }
        }
        return last
    }

    /// Asset löschen.
    static func deleteAsset(token: String, appId: String, assetId: String) async -> Bool {
        let (code, _) = await req("/oauth2/applications/\(appId)/assets/\(assetId)",
                                  token: token, method: "DELETE")
        return code >= 200 && code <= 299
    }

    static func appAssetUrl(appId: String, assetId: String) -> String {
        return "https://cdn.discordapp.com/app-assets/\(appId)/\(assetId).png"
    }
}
