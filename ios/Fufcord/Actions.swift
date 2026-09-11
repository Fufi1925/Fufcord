/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import Foundation
import UIKit

/// Geteilte Aktionen: Presets anwenden, Preset-Bilder hochladen, Bilder komprimieren.
enum Actions {

    static func localImage(_ name: String) -> UIImage? {
        if let p = Bundle.main.path(forResource: name, ofType: "png") {
            return UIImage(contentsOfFile: p)
        }
        return nil
    }

    static func localPngData(_ name: String) -> Data? {
        if let u = Bundle.main.url(forResource: name, withExtension: "png") {
            return try? Data(contentsOf: u)
        }
        return nil
    }

    /// Preset anwenden: Activity + App-ID übernehmen, live aktualisieren, Bild auto-hochladen.
    static func applyPreset(_ p: Preset, notify: @escaping (String) -> Void) {
        let store = Store.shared
        store.act = p.act
        if validAppId(p.appId) { store.appId = p.appId }
        store.saveAct()
        if RpcManager.shared.isRunning { RpcManager.shared.refresh() }
        notify("✅ Preset '\(p.title)' geladen!")
        maybeUploadPresetImage(p, notify: notify)
    }

    /// Lädt das Preset-Bild automatisch in die Discord-App hoch (falls noch nicht da).
    static func maybeUploadPresetImage(_ p: Preset, notify: @escaping (String) -> Void) {
        let asset = p.act.largeImage.trimmingCharacters(in: .whitespaces)
        if asset.isEmpty || p.icon.isEmpty { return }
        guard localImage(p.icon) != nil else { return }
        let store = Store.shared
        if store.token.isEmpty || !validAppId(store.appId) {
            notify("💡 Für Preset-Bilder: Token + App-ID eintragen!")
            return
        }
        notify("⏳ Prüfe Preset-Bild …")
        Task {
            let (ok, assets) = await DiscordApi.listAssets(token: store.token, appId: store.appId)
            if ok && assets.contains(where: { $0.1 == asset }) { return }
            guard let data = localPngData(p.icon) else { return }
            let url = "data:image/png;base64," + data.base64EncodedString()
            let (ok2, res2) = await DiscordApi.uploadAsset(token: store.token, appId: store.appId,
                                                           name: asset, imageDataUrl: url)
            await MainActor.run {
                if ok2 { notify("✅ Bild '\(asset)' hochgeladen! (5 Min warten)") }
                else { notify(String("⚠️ Bild-Upload: \(res2)".prefix(150))) }
            }
        }
    }

    /// Prüft beim Start, ob die genutzten Preset-Bilder schon hochgeladen sind.
    static func ensureActivityImages(notify: @escaping (String) -> Void) {
        let store = Store.shared
        let a = store.act
        let names = [a.largeImage, a.smallImage].map {
            $0.trimmingCharacters(in: .whitespaces).lowercased()
        }.filter { !$0.isEmpty }
        if names.isEmpty { return }
        if store.token.isEmpty || !validAppId(store.appId) || store.safeMode { return }
        Task {
            let (ok, assets) = await DiscordApi.listAssets(token: store.token, appId: store.appId)
            if !ok { return }
            let existing = Set(assets.map { $0.1 })
            for n in names {
                if existing.contains(n) { continue }
                guard let data = localPngData(n) else { continue }
                let url = "data:image/png;base64," + data.base64EncodedString()
                let (ok2, _) = await DiscordApi.uploadAsset(token: store.token, appId: store.appId,
                                                            name: n, imageDataUrl: url)
                if ok2 {
                    await MainActor.run { notify("✅ Bild '\(n)' hochgeladen! (5 Min warten)") }
                }
                break // max 1 pro Start
            }
        }
    }

    /// Komprimiert ein Foto für Discord: max 1024px, PNG (Transparenz) oder JPEG ≤ 480 KB.
    static func compressForDiscord(_ img: UIImage) -> (Data, String)? {
        var im = img
        let maxSide = max(im.size.width, im.size.height)
        if maxSide > 1024 && maxSide > 0 {
            let sc = 1024 / maxSide
            im = render(im, size: CGSize(width: im.size.width * sc, height: im.size.height * sc))
        }
        var hasAlpha = false
        if let cg = im.cgImage {
            switch cg.alphaInfo {
            case .none, .noneSkipLast, .noneSkipFirst:
                hasAlpha = false
            default:
                hasAlpha = true
            }
        }
        if hasAlpha, let d = im.pngData(), d.count <= 480 * 1024 {
            return (d, "image/png")
        }
        var cur = flatten(im)
        var q: CGFloat = 0.92
        for _ in 0..<8 {
            if let d = cur.jpegData(compressionQuality: q), d.count <= 480 * 1024 {
                return (d, "image/jpeg")
            }
            q -= 0.12
            if q < 0.45 {
                q = 0.85
                cur = render(cur, size: CGSize(width: max(64, cur.size.width * 0.8),
                                              height: max(64, cur.size.height * 0.8)))
            }
        }
        return (cur.jpegData(compressionQuality: 0.7) ?? Data(), "image/jpeg")
    }

    static func render(_ img: UIImage, size: CGSize) -> UIImage {
        let r = UIGraphicsImageRenderer(size: size)
        return r.image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
    }

    static func flatten(_ img: UIImage) -> UIImage {
        let r = UIGraphicsImageRenderer(size: img.size)
        return r.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: img.size))
            img.draw(in: CGRect(origin: .zero, size: img.size))
        }
    }
}
