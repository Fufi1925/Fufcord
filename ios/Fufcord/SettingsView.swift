/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: Store
    @State private var banner = ""
    @State private var showFetch = false
    @State private var showApps = false
    @State private var showWipe = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Banner(text: banner)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Token")
                            .font(.headline)
                            .foregroundColor(.white)
                        SecureField("Discord-Token einfügen", text: $store.token)
                            .foregroundColor(.fPrimary)
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.fStroke, lineWidth: 1))
                        HStack(spacing: 10) {
                            SecBtn(title: "Testen", icon: "checkmark.circle") { testToken() }
                            SecBtn(title: "Automatisch holen", icon: "wand.and.stars") { showFetch = true }
                        }
                        Text("⚠️ Token = Passwort! Niemandem zeigen.")
                            .font(.caption)
                            .foregroundColor(.fWarning)
                    }
                    .glassCard()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("App-ID")
                            .font(.headline)
                            .foregroundColor(.white)
                        TextField("Discord Application ID", text: $store.appId)
                            .foregroundColor(.fPrimary)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.fStroke, lineWidth: 1))
                        HStack(spacing: 10) {
                            SecBtn(title: "Testen", icon: "checkmark.circle") { testApp() }
                            SecBtn(title: "Automatisch", icon: "wand.and.stars") { showApps = true }
                        }
                        Text("Nötig für Bilder & Buttons. Leer lassen = nur Text (mit Sicher-Modus).")
                            .font(.caption)
                            .foregroundColor(.fTertiary)
                    }
                    .glassCard()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Verhalten")
                            .font(.headline)
                            .foregroundColor(.white)
                        Toggle("🛡️ Sicher-Modus (nur Text)", isOn: $store.safeMode)
                            .foregroundColor(.fPrimary)
                        Toggle("🎧 Hintergrund-Modus (lautloses Audio)", isOn: $store.bgAudio)
                            .foregroundColor(.fPrimary)
                        Text("Hält Fufcord aktiv, wenn die App minimiert ist. Lautlos & sparsam — kann in den Einstellungen ausgeschaltet werden.")
                            .font(.caption)
                            .foregroundColor(.fTertiary)
                    }
                    .glassCard()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Gefahrenzone")
                            .font(.headline)
                            .foregroundColor(.fDanger)
                        DangerBtn(title: "Alles löschen", icon: "trash") { showWipe = true }
                        Text("Löscht Token, App-ID, Presets und alle Einstellungen.")
                            .font(.caption)
                            .foregroundColor(.fTertiary)
                    }
                    .glassCard()
                    HStack {
                        Text("Version")
                            .foregroundColor(.fSecondary)
                        Spacer()
                        Text("v\(UpdateCheck.current()) (iOS)")
                            .bold()
                            .foregroundColor(.fCyan)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard))
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Einstellungen")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showFetch) { TokenFetchView().preferredColorScheme(.dark) }
            .sheet(isPresented: $showApps) { AppPickerView().preferredColorScheme(.dark) }
            .alert("Wirklich alles löschen?", isPresented: $showWipe) {
                Button("Alles löschen", role: .destructive) {
                    RpcManager.shared.stop()
                    store.clearAll()
                }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Token, App-ID, Presets und Einstellungen werden gelöscht.")
            }
        }
    }

    private func testToken() {
        let t = store.token.trimmingCharacters(in: .whitespaces)
        if t.isEmpty {
            show("Bitte erst Token eintragen!")
            return
        }
        show("⏳ Teste Token …")
        Task {
            let (ok, name) = await DiscordApi.getMe(t)
            await MainActor.run {
                show(ok ? "✅ Token gültig! Hallo, \(name)!" : "❌ Ungültig (HTTP \(name))")
            }
        }
    }

    private func testApp() {
        let a = store.appId.trimmingCharacters(in: .whitespaces)
        if a.isEmpty {
            show("Keine App-ID → läuft im Text-Modus")
            return
        }
        if !validAppId(a) {
            show("❌ Keine gültige App-ID (Zahlen, 15+ Stellen)")
            return
        }
        if store.token.isEmpty {
            show("Bitte erst Token eintragen!")
            return
        }
        show("⏳ Teste App-ID …")
        Task {
            let (ok, assets) = await DiscordApi.listAssets(token: store.token, appId: a)
            await MainActor.run {
                show(ok ? "✅ App gefunden! (\(assets.count) Assets)" : "❌ App nicht gefunden (404?)")
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
