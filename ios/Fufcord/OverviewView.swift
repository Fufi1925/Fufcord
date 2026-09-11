/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

struct OverviewView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var rpc: RpcManager
    @State private var banner = ""
    @State private var presets: [Preset] = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    header
                    statusCard
                    Banner(text: banner)
                    Text("Live-Vorschau")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    PreviewCard(act: store.act, safe: store.safeMode)
                    Text("Presets")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    presetChips
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Übersicht")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                presets = store.bundledPresets() + store.customPresets()
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.fViolet)
                    .frame(width: 48, height: 48)
                Image(systemName: "bolt.fill")
                    .font(.title2)
                    .foregroundColor(.white)
            }
            VStack(alignment: .leading) {
                Text("Fufcord")
                    .font(.title2)
                    .bold()
                    .foregroundColor(.white)
                Text("Custom Discord Rich Presence")
                    .font(.caption)
                    .foregroundColor(.fSecondary)
            }
            Spacer()
            Text("v\(UpdateCheck.current())")
                .font(.caption)
                .bold()
                .foregroundColor(.fCyan)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
        }
    }

    private var statusCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                Circle()
                    .fill(rpc.isRunning ? Color.fMint : Color.fTertiary)
                    .frame(width: 12, height: 12)
                VStack(alignment: .leading, spacing: 2) {
                    Text(rpc.isRunning ? "Status läuft" : "Status gestoppt")
                        .bold()
                        .foregroundColor(.white)
                    Text(rpc.statusText)
                        .font(.caption)
                        .foregroundColor(.fSecondary)
                }
                Spacer()
            }
            if rpc.isRunning {
                DangerBtn(title: "Stop", icon: "stop.fill") { toggle() }
            } else {
                PriBtn(title: "Start", icon: "play.fill") { toggle() }
            }
        }
        .glassCard()
    }

    private var presetChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(presets.indices, id: \.self) { i in
                    let p = presets[i]
                    Button {
                        Actions.applyPreset(p, notify: show)
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.fCard2)
                                    .frame(width: 56, height: 56)
                                if !p.icon.isEmpty, let img = Actions.localImage(p.icon) {
                                    Image(uiImage: img)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 56, height: 56)
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                } else {
                                    Image(systemName: "bolt.fill")
                                        .foregroundColor(.fCyan)
                                }
                            }
                            Text(p.title)
                                .font(.caption2)
                                .foregroundColor(.fSecondary)
                                .lineLimit(1)
                                .frame(width: 64)
                        }
                    }
                }
            }
        }
    }

    private func toggle() {
        if rpc.isRunning {
            rpc.stop()
        } else {
            if store.token.isEmpty {
                show("Erst Token eintragen! (Tab Einstellungen)")
                return
            }
            rpc.start()
            show("Starte … (läuft im Hintergrund weiter 🎧)")
            Actions.ensureActivityImages(notify: show)
            let a = store.act
            if !a.largeImage.isEmpty || !a.smallImage.isEmpty {
                if store.safeMode {
                    show("💡 Sicher-Modus an: läuft ohne Bilder!")
                } else if !validAppId(store.appId) {
                    show("💡 Keine gültige App-ID: ohne Bilder! (Einstellungen)")
                } else {
                    show("🎉 Status läuft! (Bild braucht ~5 Min)")
                }
            }
        }
    }

    private func show(_ s: String) {
        banner = s
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if banner == s { banner = "" }
        }
    }
}
