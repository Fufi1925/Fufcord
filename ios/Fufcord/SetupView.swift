/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

/// 3-Schritte-Setup: Token → App-ID → Fertig (Port von Androids SetupActivity).
struct SetupView: View {
    @EnvironmentObject var store: Store
    @State private var step = 1
    @State private var banner = ""
    @State private var showFetch = false
    @State private var showApps = false
    @State private var askStep = 0 // 0 = keine, 1/2 = Token-Fragen

    var body: some View {
        NavigationStack {
            ZStack {
                ScrollView {
                    VStack(spacing: 16) {
                        stepDots
                        Banner(text: banner)
                        if step == 1 { stepToken }
                        if step == 2 { stepApp }
                        if step == 3 { stepDone }
                    }
                    .padding()
                }
                if askStep > 0 {
                    Color.black.opacity(0.6)
                        .ignoresSafeArea()
                    askCard
                        .padding(32)
                }
            }
            .background(Color.fBg)
            .navigationTitle("Einrichtung")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if store.token.isEmpty && !store.tokenAsked {
                    store.tokenAsked = true
                    askStep = 1
                }
            }
            .sheet(isPresented: $showFetch) { TokenFetchView().preferredColorScheme(.dark) }
            .sheet(isPresented: $showApps) { AppPickerView().preferredColorScheme(.dark) }
        }
    }

    private var stepDots: some View {
        HStack(spacing: 8) {
            ForEach(1...3, id: \.self) { i in
                Circle()
                    .fill(i <= step ? Color.fViolet : Color.fCard2)
                    .frame(width: 10, height: 10)
            }
        }
    }

    private var stepToken: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Schritt 1: Token")
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
            PriBtn(title: "Weiter", icon: "arrow.right") {
                if store.token.trimmingCharacters(in: .whitespaces).isEmpty {
                    show("Bitte erst Token eintragen oder automatisch holen!")
                } else {
                    step = 2
                }
            }
        }
        .glassCard()
    }

    private var stepApp: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Schritt 2: App-ID (optional)")
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
            Text("Nötig für Bilder & Buttons — oder leer lassen (Sicher-Modus: nur Text).")
                .font(.caption)
                .foregroundColor(.fTertiary)
            HStack(spacing: 10) {
                SecBtn(title: "Zurück", icon: "arrow.left") { step = 1 }
                PriBtn(title: "Weiter", icon: "arrow.right") { step = 3 }
            }
        }
        .glassCard()
    }

    private var stepDone: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Schritt 3: Fertig! 🎉")
                .font(.headline)
                .foregroundColor(.white)
            Text("Alles bereit! Tippe auf Fertig und starte deinen Status auf der Übersicht.")
                .font(.subheadline)
                .foregroundColor(.fSecondary)
            HStack(spacing: 10) {
                SecBtn(title: "Zurück", icon: "arrow.left") { step = 2 }
                PriBtn(title: "Fertig", icon: "checkmark") { store.setupDone = true }
            }
        }
        .glassCard()
    }

    private var askCard: some View {
        VStack(spacing: 12) {
            if askStep == 1 {
                Text("🎫")
                    .font(.largeTitle)
                Text("Hast du schon einen Discord-Token?")
                    .bold()
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                PriBtn(title: "Ja, ich füge ihn ein") { askStep = 0 }
                SecBtn(title: "Nein") { askStep = 2 }
            } else {
                Text("✨")
                    .font(.largeTitle)
                Text("Soll ich ihn automatisch für dich holen? Einfach einloggen — fertig!")
                    .bold()
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                PriBtn(title: "Ja, automatisch holen") {
                    askStep = 0
                    showFetch = true
                }
                SecBtn(title: "Nein, ich mache es selbst") { askStep = 0 }
            }
        }
        .glassCard()
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

// MARK: - App-Auswahl (Liste + neu anlegen)

struct AppPickerView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var apps: [(String, String)] = []
    @State private var loading = true
    @State private var status = ""
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !status.isEmpty {
                        Text(status)
                            .font(.subheadline)
                            .foregroundColor(.fSecondary)
                    }
                    if loading {
                        Text("⏳ Lade Apps …")
                            .foregroundColor(.fSecondary)
                    }
                    ForEach(apps.indices, id: \.self) { i in
                        Button {
                            store.appId = apps[i].0
                            dismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(apps[i].1)
                                    .bold()
                                    .foregroundColor(.fPrimary)
                                Text(apps[i].0)
                                    .font(.caption)
                                    .foregroundColor(.fTertiary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.fCard))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.fStroke, lineWidth: 1))
                        }
                    }
                    Text("Oder neu erstellen:")
                        .font(.headline)
                        .foregroundColor(.white)
                        .padding(.top, 8)
                    TextField("App-Name (z. B. Mein Status)", text: $newName)
                        .foregroundColor(.fPrimary)
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.fStroke, lineWidth: 1))
                    PriBtn(title: "App erstellen & übernehmen", icon: "plus") { create() }
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("App wählen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Schließen") { dismiss() }
                }
            }
            .task {
                if store.token.isEmpty {
                    loading = false
                    status = "Bitte erst Token eintragen!"
                    return
                }
                let (ok, list) = await DiscordApi.listApps(token: store.token)
                loading = false
                if ok {
                    apps = list
                    if list.isEmpty { status = "Keine Apps gefunden — erstelle unten eine neue." }
                } else {
                    status = "❌ Konnte Apps nicht laden (Token prüfen)."
                }
            }
        }
    }

    private func create() {
        let n = newName.trimmingCharacters(in: .whitespaces)
        if n.count < 2 {
            status = "Bitte einen Namen eingeben (min. 2 Zeichen)!"
            return
        }
        status = "⏳ Erstelle App …"
        Task {
            let (ok, res) = await DiscordApi.createApp(token: store.token, name: n)
            await MainActor.run {
                if ok {
                    store.appId = res
                    dismiss()
                } else {
                    status = "❌ \(res)"
                }
            }
        }
    }
}
