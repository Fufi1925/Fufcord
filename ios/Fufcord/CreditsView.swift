/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI
import UIKit

struct CreditsView: View {
    @EnvironmentObject var store: Store
    @State private var banner = ""
    @State private var devName = "Fufi"
    @State private var devHandle = "@fufi"
    @State private var devAvatar: URL?

    private let devId = "1303627964734246944"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Banner(text: banner)
                    VStack(spacing: 10) {
                        ZStack {
                            Circle()
                                .fill(Color.fViolet)
                                .frame(width: 84, height: 84)
                            if let u = devAvatar {
                                AsyncImage(url: u) { im in
                                    im.resizable().scaledToFill()
                                } placeholder: {
                                    Text("F").font(.largeTitle).bold().foregroundColor(.white)
                                }
                                .frame(width: 84, height: 84)
                                .clipShape(Circle())
                            } else {
                                Text("F")
                                    .font(.largeTitle)
                                    .bold()
                                    .foregroundColor(.white)
                            }
                        }
                        Text(devName)
                            .font(.title2)
                            .bold()
                            .foregroundColor(.white)
                        Text(devHandle)
                            .font(.subheadline)
                            .foregroundColor(.fTertiary)
                        Text("Entwickler & Designer")
                            .font(.caption)
                            .bold()
                            .foregroundColor(.fCyan)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
                        HStack(spacing: 8) {
                            Text("ID: \(devId)")
                                .font(.caption)
                                .foregroundColor(.fTertiary)
                            Button {
                                UIPasteboard.general.string = devId
                                show("📋 ID kopiert!")
                            } label: {
                                Image(systemName: "doc.on.doc")
                                    .font(.caption)
                                    .foregroundColor(.fCyan)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .glassCard()
                    Link(destination: URL(string: "https://discord.gg/8EzjRTksJP")!) {
                        linkLabel(icon: "message.fill", title: "Discord beitreten", sub: "Support & Community")
                    }
                    Link(destination: URL(string: "https://github.com/Fufi1925/Fufcord")!) {
                        linkLabel(icon: "chevron.left.forwardslash.chevron.right", title: "GitHub",
                                  sub: "Quellcode & Updates")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Über Fufcord")
                            .font(.headline)
                            .foregroundColor(.white)
                        Text("Custom Discord Rich Presence fürs iPhone — Status, Bilder, Buttons und Timer, ganz ohne PC. Gebaut mit ganz viel ❤️ von Fufi.")
                            .font(.subheadline)
                            .foregroundColor(.fSecondary)
                        HStack {
                            Text("Version")
                                .font(.caption)
                                .foregroundColor(.fTertiary)
                            Spacer()
                            Text("v\(UpdateCheck.current()) (iOS)")
                                .font(.caption)
                                .bold()
                                .foregroundColor(.fCyan)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glassCard()
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Danke an")
                            .font(.headline)
                            .foregroundColor(.white)
                        thanksRow("Dich", "fürs Nutzen & Feedback 💜")
                        thanksRow("Die Community", "für Ideen & Tests 🙏")
                        thanksRow("Discord", "für die API 🎮")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glassCard()
                    Text("© 2026 Fufcord • Mit ❤️ gemacht")
                        .font(.caption)
                        .foregroundColor(.fTertiary)
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Credits")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                if store.token.isEmpty { return }
                let u = await DiscordApi.getUser(token: store.token, userId: devId)
                if u.ok {
                    devName = u.name
                    devHandle = u.handle
                    devAvatar = URL(string: u.avatarUrl)
                }
            }
        }
    }

    private func linkLabel(icon: String, title: String, sub: String) -> some View {
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
            Image(systemName: "arrow.up.right")
                .foregroundColor(.fTertiary)
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.fStroke, lineWidth: 1))
    }

    private func thanksRow(_ who: String, _ why: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.fMint)
            Text(who)
                .bold()
                .foregroundColor(.fPrimary)
            Text(why)
                .font(.subheadline)
                .foregroundColor(.fSecondary)
            Spacer()
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
