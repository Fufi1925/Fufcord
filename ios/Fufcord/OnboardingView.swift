/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

/// Erste Schritte: 3 Willkommens-Seiten (Port von Androids Onboarding).
struct OnboardingView: View {
    @EnvironmentObject var store: Store
    @State private var page = 0

    var body: some View {
        VStack {
            TabView(selection: $page) {
                slide(icon: "bolt.fill", title: "Willkommen bei Fufcord!",
                      text: "Dein eigener Discord-Status — mit Bildern, Buttons und Timer. Direkt vom iPhone, ganz ohne PC.")
                    .tag(0)
                slide(icon: "wand.and.stars", title: "In 2 Minuten bereit",
                      text: "Token automatisch holen, App-ID automatisch erstellen — die App richtet fast alles für dich ein.")
                    .tag(1)
                slide(icon: "headphones", title: "Läuft im Hintergrund",
                      text: "Dank lautlosem Audio bleibt dein Status aktiv, auch wenn du die App minimierst.")
                    .tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))
            PriBtn(title: page < 2 ? "Weiter" : "Los geht's!", icon: page < 2 ? "arrow.right" : "checkmark") {
                if page < 2 {
                    page += 1
                } else {
                    store.onboardingDone = true
                }
            }
            .padding()
        }
        .background(Color.fBg)
    }

    private func slide(icon: String, title: String, text: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            ZStack {
                RoundedRectangle(cornerRadius: 24)
                    .fill(Color.fViolet)
                    .frame(width: 96, height: 96)
                Image(systemName: icon)
                    .font(.system(size: 44))
                    .foregroundColor(.white)
            }
            Text(title)
                .font(.title)
                .bold()
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            Text(text)
                .font(.body)
                .foregroundColor(.fSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
        }
    }
}
