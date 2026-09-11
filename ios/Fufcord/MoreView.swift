/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI
import UIKit

struct MoreView: View {
    @EnvironmentObject var store: Store
    @State private var showEditor = false
    @State private var showHelp = false
    @State private var banner = ""
    @State private var updateTag = ""
    @State private var updateNotes = ""
    @State private var updateUrl = ""
    @State private var showUpdate = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    Banner(text: banner)
                    row(icon: "paintbrush.fill", title: "Studio", sub: "Status bearbeiten & Bilder hochladen") {
                        showEditor = true
                    }
                    ShareLink(item: "🎮 Fufcord — Custom Discord Rich Presence fürs iPhone!\nhttps://github.com/Fufi1925/Fufcord") {
                        rowContent(icon: "square.and.arrow.up", title: "Teilen",
                                   sub: "Fufcord weiterempfehlen")
                    }
                    row(icon: "questionmark.circle.fill", title: "Hilfe", sub: "Anleitungen & Tipps") {
                        showHelp = true
                    }
                    row(icon: "arrow.down.circle.fill", title: "Update prüfen", sub: "Nach neuer Version suchen") {
                        manualUpdate()
                    }
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
            .navigationTitle("Mehr")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showEditor) { EditorView().preferredColorScheme(.dark) }
            .sheet(isPresented: $showHelp) { HelpView().preferredColorScheme(.dark) }
            .alert("Update verfügbar: v\(updateTag)", isPresented: $showUpdate) {
                Button("Herunterladen") {
                    if let u = URL(string: updateUrl) {
                        UIApplication.shared.open(u)
                    }
                }
                Button("Überspringen") { store.skipVersion = updateTag }
                Button("Später", role: .cancel) {}
            } message: {
                Text(updateNotes.isEmpty ? "Eine neue Version ist verfügbar." : String(updateNotes.prefix(600)))
            }
        }
    }

    private func row(icon: String, title: String, sub: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            rowContent(icon: icon, title: title, sub: sub)
        }
    }

    private func rowContent(icon: String, title: String, sub: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.fCyan)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .bold()
                    .foregroundColor(.fPrimary)
                Text(sub)
                    .font(.caption)
                    .foregroundColor(.fTertiary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundColor(.fTertiary)
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.fStroke, lineWidth: 1))
    }

    private func manualUpdate() {
        Task {
            if let u = await UpdateCheck.check() {
                await MainActor.run {
                    updateTag = u.tag
                    updateNotes = u.notes
                    updateUrl = u.url
                    showUpdate = true
                }
            } else {
                await MainActor.run {
                    show("✅ Du hast die neueste Version!")
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

// MARK: - Hilfe

struct HelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    helpCard("🚀 Schnellstart", """
                    1. Token eintragen (Einstellungen → Token testen)
                    2. App-ID eintragen oder automatisch erstellen
                    3. Übersicht → Start — fertig! 🎉
                    """)
                    helpCard("🎫 Token holen", """
                    • In der App: Setup bzw. Einstellungen → „Automatisch holen" — einloggen, fertig.
                    • Manuell: Discord im Browser → F12 → Konsole → localStorage-Beleg suchen (nur Discord-Seite, nie woanders eingeben!)
                    • ⚠️ Token = Passwort! Niemandem zeigen.
                    """)
                    helpCard("🖼️ Eigene Bilder", """
                    • Studio → Bild hochladen → Name vergeben (a-z, 0-9, _)
                    • Wichtig: Bild heißt in Discord genau wie der Name — Groß-/Kleinschreibung egal, alles wird klein.
                    • Nach Upload ~5 Minuten warten, dann erscheint das Bild.
                    • Preset-Bilder lädt die App automatisch hoch.
                    """)
                    helpCard("🎧 Hintergrund-Modus", """
                    • Die App spielt lautloses Audio, damit iOS sie nicht stoppt.
                    • Bitte Nicht stören / Energiesparen prüfen, falls der Status abbricht.
                    • Sideload-Apps (AltStore) müssen alle 7 Tage aktualisiert werden — sonst stoppt iOS sie.
                    """)
                    helpCard("🛡️ Sicher-Modus", """
                    • Zeigt nur Text — geht immer, auch ohne App-ID.
                    • Ideal zum Testen, ob der Token funktioniert.
                    """)
                    helpCard("❓ Bild fehlt?", """
                    1. App-ID korrekt? (Einstellungen prüfen)
                    2. Asset-Name im Studio = Bild-Auswahl? (klein geschrieben?)
                    3. Studio → Assets: Bild vorhanden? Sonst neu hochladen.
                    4. 5+ Minuten gewartet? Discord braucht Geduld.
                    5. Sicher-Modus AUS? (Der blendet Bilder aus.)
                    """)
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Hilfe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Schließen") { dismiss() }
                }
            }
        }
    }

    private func helpCard(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .bold()
                .foregroundColor(.white)
            Text(text.trimmingCharacters(in: .whitespacesAndNewlines))
                .font(.subheadline)
                .foregroundColor(.fSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}
