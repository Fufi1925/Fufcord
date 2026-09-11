/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

// MARK: - Fufcord-Farben (wie Android)

extension Color {
    static let fBg = Color(red: 11 / 255, green: 13 / 255, blue: 18 / 255)
    static let fCard = Color(red: 20 / 255, green: 23 / 255, blue: 30 / 255)
    static let fCard2 = Color(red: 26 / 255, green: 31 / 255, blue: 40 / 255)
    static let fStroke = Color(red: 38 / 255, green: 44 / 255, blue: 56 / 255)
    static let fViolet = Color(red: 88 / 255, green: 101 / 255, blue: 242 / 255)
    static let fCyan = Color(red: 139 / 255, green: 157 / 255, blue: 249 / 255)
    static let fMint = Color(red: 87 / 255, green: 242 / 255, blue: 135 / 255)
    static let fDanger = Color(red: 237 / 255, green: 66 / 255, blue: 69 / 255)
    static let fWarning = Color(red: 240 / 255, green: 178 / 255, blue: 50 / 255)
    static let fPrimary = Color(red: 242 / 255, green: 243 / 255, blue: 245 / 255)
    static let fSecondary = Color(red: 154 / 255, green: 163 / 255, blue: 178 / 255)
    static let fTertiary = Color(red: 95 / 255, green: 107 / 255, blue: 125 / 255)
}

// MARK: - Karten & Buttons

struct GlassCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(18)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.fCard))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.fStroke, lineWidth: 1))
    }
}

extension View {
    func glassCard() -> some View { modifier(GlassCard()) }
}

struct PriBtn: View {
    var title: String
    var icon: String?
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let i = icon { Image(systemName: i) }
                Text(title).bold()
            }
            .frame(maxWidth: .infinity)
            .padding()
            .foregroundColor(.white)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.fViolet))
        }
    }
}

struct SecBtn: View {
    var title: String
    var icon: String?
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let i = icon { Image(systemName: i).foregroundColor(.fCyan) }
                Text(title).bold().foregroundColor(.fPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard2))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.fStroke, lineWidth: 1))
        }
    }
}

struct DangerBtn: View {
    var title: String
    var icon: String?
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let i = icon { Image(systemName: i) }
                Text(title).bold()
            }
            .frame(maxWidth: .infinity)
            .padding()
            .foregroundColor(.white)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.fDanger))
        }
    }
}

/// Kleine Meldungszeile (Toast-Ersatz).
struct Banner: View {
    var text: String
    var body: some View {
        if !text.isEmpty {
            Text(text)
                .font(.subheadline)
                .foregroundColor(.fPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.fCard2))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.fStroke, lineWidth: 1))
        }
    }
}
