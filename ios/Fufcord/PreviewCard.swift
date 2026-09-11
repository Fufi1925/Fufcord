/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

/// Discord-ähnliche Live-Vorschau (Port von Androids PreviewBinder).
struct PreviewCard: View {
    var act: ActConfig
    var safe: Bool

    @EnvironmentObject var store: Store
    @State private var largeUrl: URL?
    @State private var smallUrl: URL?
    static var urlCache: [String: String] = [:]

    private var typeLabel: String {
        switch act.type {
        case 1: return "STREAMT"
        case 2: return "HÖRT"
        case 3: return "SCHAUT"
        case 5: return "TRITT AN IN"
        default: return "SPIELT EIN SPIEL"
        }
    }

    private var timeLabel: String {
        if act.timestampMode == 1 && act.timestampOffsetSec > 0 {
            return "⏱ seit \(act.offsetLabel()) (+ live)"
        }
        return "⏱ läuft seit Start"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.fViolet)
                    .frame(width: 4, height: 18)
                Text(typeLabel)
                    .font(.caption)
                    .bold()
                    .foregroundColor(.white)
                    .tracking(1)
            }
            HStack(alignment: .top, spacing: 14) {
                if !safe && !act.largeImage.isEmpty {
                    ZStack(alignment: .bottomTrailing) {
                        imageBox(name: act.largeImage, url: largeUrl, size: 96)
                        if !act.smallImage.isEmpty {
                            imageBox(name: act.smallImage, url: smallUrl, size: 36)
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(act.name.isEmpty ? "Fufcord" : act.name)
                        .bold()
                        .font(.headline)
                        .foregroundColor(.white)
                    if !act.details.isEmpty {
                        Text(act.details).font(.subheadline).foregroundColor(.fSecondary)
                    }
                    if !act.state.isEmpty {
                        Text(act.state).font(.subheadline).foregroundColor(.fSecondary)
                    }
                    if act.useTimestamp {
                        Text(timeLabel).font(.caption).foregroundColor(.fMint)
                    }
                }
                Spacer()
            }
            if !safe && !act.buttons.isEmpty {
                HStack(spacing: 8) {
                    ForEach(0..<min(2, act.buttons.count), id: \.self) { i in
                        Text(act.buttons[i].label)
                            .font(.subheadline)
                            .bold()
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.fCard2))
                    }
                }
            }
            if safe {
                HStack(spacing: 6) {
                    Image(systemName: "shield.fill").foregroundColor(.fMint)
                    Text("Sicher-Modus: nur Text, keine Bilder/Buttons")
                        .font(.caption)
                        .foregroundColor(.fMint)
                }
            }
        }
        .glassCard()
        .task(id: act.largeImage + "|" + act.smallImage + "|" + store.appId) {
            largeUrl = await resolveOne(act.largeImage)
            smallUrl = await resolveOne(act.smallImage)
        }
    }

    @ViewBuilder
    private func imageBox(name: String, url: URL?, size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.fCard2)
                .frame(width: size, height: size)
            if let img = localPreview(name) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            } else if let u = url {
                AsyncImage(url: u) { im in
                    im.resizable().scaledToFill()
                } placeholder: {
                    Text("🖼️")
                }
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            } else {
                Text(size > 50 ? "🖼️\n\(name)" : name)
                    .font(size > 50 ? .caption2 : .system(size: 7))
                    .foregroundColor(.fTertiary)
                    .multilineTextAlignment(.center)
                    .padding(4)
            }
        }
        .frame(width: size, height: size)
    }

    /// Lokales Bild — Custom-Uploads zeigen IMMER das Server-Bild (wie Android v1.14).
    private func localPreview(_ name: String) -> UIImage? {
        if store.customAssets.contains(name) { return nil }
        return Actions.localImage(name)
    }

    private func resolveOne(_ name: String) async -> URL? {
        let n = name.trimmingCharacters(in: .whitespaces)
        if n.isEmpty { return nil }
        if localPreview(n) != nil { return nil } // lokal vorhanden → kein Server nötig
        let key = "\(store.appId)/\(n)"
        if let u = PreviewCard.urlCache[key] { return URL(string: u) }
        if store.appId.isEmpty || store.token.isEmpty { return nil }
        let (ok, assets) = await DiscordApi.listAssets(token: store.token, appId: store.appId)
        if ok, let hit = assets.first(where: { $0.1 == n }) {
            let u = DiscordApi.appAssetUrl(appId: store.appId, assetId: hit.0)
            PreviewCard.urlCache[key] = u
            return URL(string: u)
        }
        return nil
    }
}
