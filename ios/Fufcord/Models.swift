/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation

// MARK: - Kleine Helfer

func s(_ v: Any?, _ def: String = "") -> String {
    return (v as? String) ?? def
}

func jsonDict(_ s: String) -> [String: Any]? {
    if let d = s.data(using: .utf8),
       let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] {
        return o
    }
    return nil
}

func jsonString(_ dict: [String: Any]) -> String {
    if let d = try? JSONSerialization.data(withJSONObject: dict),
       let str = String(data: d, encoding: .utf8) {
        return str
    }
    return "{}"
}

func validAppId(_ appId: String) -> Bool {
    let a = appId.trimmingCharacters(in: .whitespaces)
    if a.isEmpty || a.hasPrefix("DEINE") { return false }
    return a.allSatisfy({ $0.isNumber }) && a.count >= 15
}

// MARK: - Activity-Modell (1:1-Port von Android)

struct ActButton {
    var label: String
    var url: String
}

struct ActConfig {
    var name: String = "Fufcord"
    var type: Int = 0
    var details: String = ""
    var state: String = ""
    var largeImage: String = ""
    var largeText: String = ""
    var smallImage: String = ""
    var smallText: String = ""
    var streamUrl: String = ""
    var buttons: [ActButton] = []
    var useTimestamp: Bool = true
    var partyCurrent: Int = 0
    var partyMax: Int = 0
    var status: String = "online"
    var timestampMode: Int = 0 // 0 = ab Start, 1 = eigener Beginn
    var timestampOffsetSec: Int64 = 0

    static let TYPES: [Int: String] = [
        0: "Spielt", 1: "Streamt", 2: "Hört",
        3: "Schaut zu", 4: "Benutzerdefiniert", 5: "Tritt an in"
    ]
    static let STATUS: [String: String] = [
        "online": "🟢 Online", "idle": "🌙 Abwesend",
        "dnd": "⛔ Bitte nicht stören", "invisible": "⚫ Unsichtbar"
    ]

    func toDict() -> [String: Any] {
        var btns: [[String: String]] = []
        for b in buttons { btns.append(["label": b.label, "url": b.url]) }
        return [
            "name": name, "type": type,
            "details": details, "state": state,
            "large_image": largeImage, "large_text": largeText,
            "small_image": smallImage, "small_text": smallText,
            "stream_url": streamUrl, "buttons": btns,
            "use_timestamp": useTimestamp,
            "party_current": partyCurrent, "party_max": partyMax,
            "status": status, "timestamp_mode": timestampMode,
            "timestamp_offset_sec": timestampOffsetSec
        ]
    }

    /// Baut das Discord-Activity-Objekt. safe=true → nur Text (geht immer).
    func toPresence(appId: String, safe: Bool, startTs: Int64) -> [String: Any] {
        var a: [String: Any] = [:]
        a["name"] = name.isEmpty ? "Fufcord" : name
        a["type"] = type
        a["created_at"] = Int64(Date().timeIntervalSince1970 * 1000)
        var start = startTs
        if timestampMode == 1 && timestampOffsetSec > 0 {
            start = startTs - timestampOffsetSec * 1000
        }
        if !details.isEmpty { a["details"] = String(details.prefix(128)) }
        if !state.isEmpty { a["state"] = String(state.prefix(128)) }
        if safe {
            if useTimestamp { a["timestamps"] = ["start": start] }
            return a
        }
        let hasApp = validAppId(appId)
        if hasApp { a["application_id"] = appId }
        if hasApp {
            var assets: [String: Any] = [:]
            if !largeImage.isEmpty { assets["large_image"] = String(largeImage.prefix(256)) }
            if !largeText.isEmpty { assets["large_text"] = String(largeText.prefix(128)) }
            if !smallImage.isEmpty { assets["small_image"] = String(smallImage.prefix(256)) }
            if !smallText.isEmpty { assets["small_text"] = String(smallText.prefix(128)) }
            if !assets.isEmpty { a["assets"] = assets }
        }
        if type == 1 { a["url"] = streamUrl.isEmpty ? "https://twitch.tv/" : streamUrl }
        if hasApp && !buttons.isEmpty {
            var arr: [[String: String]] = []
            for b in buttons.prefix(2) {
                if !b.label.isEmpty && !b.url.isEmpty {
                    arr.append(["label": String(b.label.prefix(32)), "url": b.url])
                }
            }
            if !arr.isEmpty { a["buttons"] = arr }
        }
        if useTimestamp { a["timestamps"] = ["start": start] }
        if partyCurrent > 0 && partyMax > 0 {
            a["party"] = ["id": "fufcord-party", "size": [partyCurrent, partyMax]]
        }
        return a
    }

    func offsetLabel() -> String {
        return ActConfig.formatOffset(timestampOffsetSec)
    }

    static func hasEmoji(_ str: String) -> Bool {
        for sc in str.unicodeScalars {
            let g = sc.properties.generalCategory
            if g == .otherSymbol || g == .modifierSymbol || sc.value == 0x200D || sc.value == 0xFE0F {
                return true
            }
        }
        return false
    }

    static func stripEmoji(_ str: String) -> String {
        let kept = str.unicodeScalars.filter {
            let g = $0.properties.generalCategory
            return !(g == .otherSymbol || g == .modifierSymbol || $0.value == 0x200D || $0.value == 0xFE0F)
        }
        return String(String.UnicodeScalarView(kept)).trimmingCharacters(in: .whitespaces)
    }

    /// Bereinigt rohe JSON-Daten. Gibt (config, warnungen) zurück.
    static func sanitize(_ raw: [String: Any]?) -> (ActConfig, [String]) {
        var warns: [String] = []
        guard let raw = raw else {
            return (ActConfig(), ["Ungültiges Format — Standard genommen."])
        }
        var c = ActConfig()
        let t = raw["type"] as? Int ?? 0
        if TYPES[t] != nil { c.type = t } else { warns.append("Typ ungültig → 'Spielt'."); c.type = 0 }
        c.name = String(s(raw["name"], "Fufcord").prefix(256))
        c.details = String(s(raw["details"]).prefix(256))
        c.state = String(s(raw["state"]).prefix(256))
        if s(raw["details"]).count > 128 || s(raw["state"]).count > 128 {
            warns.append("Details/State zu lang → gekürzt.")
        }
        c.largeImage = String(s(raw["large_image"]).trimmingCharacters(in: .whitespaces).lowercased().prefix(256))
        c.largeText = String(s(raw["large_text"]).prefix(256))
        c.smallImage = String(s(raw["small_image"]).trimmingCharacters(in: .whitespaces).lowercased().prefix(256))
        c.smallText = String(s(raw["small_text"]).prefix(256))
        c.streamUrl = String(s(raw["stream_url"]).prefix(256))
        if let arr = raw["buttons"] as? [[String: Any]] {
            for i in 0..<min(arr.count, 2) {
                var label = s(arr[i]["label"]).trimmingCharacters(in: .whitespaces)
                let url = s(arr[i]["url"]).trimmingCharacters(in: .whitespaces)
                if hasEmoji(label) {
                    label = stripEmoji(label)
                    warns.append("Button \(i + 1): Emojis entfernt (blockieren die Anzeige!).")
                }
                if label.isEmpty { warns.append("Button \(i + 1): leer → entfernt."); continue }
                if !(url.hasPrefix("https://") || url.hasPrefix("http://")) {
                    warns.append("Button \(i + 1): Link ungültig → entfernt."); continue
                }
                c.buttons.append(ActButton(label: String(label.prefix(32)), url: url))
            }
            if arr.count > 2 { warns.append("Mehr als 2 Buttons → nur erste 2 behalten.") }
        }
        c.useTimestamp = raw["use_timestamp"] as? Bool ?? true
        c.timestampMode = (raw["timestamp_mode"] as? Int ?? 0) == 1 ? 1 : 0
        let off = raw["timestamp_offset_sec"] as? Int64 ?? Int64(raw["timestamp_offset_sec"] as? Int ?? 0)
        c.timestampOffsetSec = max(0, off)
        c.partyCurrent = max(0, raw["party_current"] as? Int ?? 0)
        c.partyMax = max(0, raw["party_max"] as? Int ?? 0)
        let st = s(raw["status"], "online").lowercased()
        c.status = STATUS[st] != nil ? st : "online"
        if c.name.trimmingCharacters(in: .whitespaces).isEmpty {
            c.name = "Fufcord"
            warns.append("Name leer → 'Fufcord'.")
        }
        return (c, warns)
    }

    /// Formatiert Sekunden als "100 J" / "2 Tg 3 Std" / "3 Std 15 Min" / "45 Min".
    static func formatOffset(_ secs: Int64) -> String {
        var r = max(0, secs)
        if r < 60 { return "0 Min" }
        let y = r / 31536000; r %= 31536000
        let d = r / 86400; r %= 86400
        let h = r / 3600; r %= 3600
        let m = r / 60
        if y > 0 { return d > 0 ? "\(y) J \(d) Tg" : "\(y) J" }
        if d > 0 { return h > 0 ? "\(d) Tg \(h) Std" : "\(d) Tg" }
        if h > 0 { return m > 0 ? "\(h) Std \(m) Min" : "\(h) Std" }
        return "\(m) Min"
    }
}

struct Preset {
    var file: String
    var title: String
    var status: String
    var appId: String
    var act: ActConfig
    var icon: String
}
