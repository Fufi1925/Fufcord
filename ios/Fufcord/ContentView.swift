/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject var store: Store
    @State private var updateTag = ""
    @State private var updateNotes = ""
    @State private var updateUrl = ""
    @State private var showUpdate = false

    var body: some View {
        TabView {
            OverviewView()
                .tabItem { Label("Übersicht", systemImage: "house.fill") }
            PresetsView()
                .tabItem { Label("Presets", systemImage: "square.stack.fill") }
            MoreView()
                .tabItem { Label("Mehr", systemImage: "square.grid.2x2.fill") }
            CreditsView()
                .tabItem { Label("Credits", systemImage: "star.fill") }
            SettingsView()
                .tabItem { Label("Einstellungen", systemImage: "slider.horizontal.3") }
        }
        .tint(.fCyan)
        .fullScreenCover(isPresented: Binding(get: { !store.setupDone }, set: { _ in })) {
            if !store.onboardingDone {
                OnboardingView()
            } else {
                SetupView()
            }
        }
        .task {
            if let u = await UpdateCheck.check() {
                updateTag = u.tag
                updateNotes = u.notes
                updateUrl = u.url
                showUpdate = true
            }
        }
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
