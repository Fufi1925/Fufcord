/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

struct PresetsView: View {
    @EnvironmentObject var store: Store
    @State private var customs: [Preset] = []
    @State private var banner = ""
    @State private var showSave = false
    @State private var saveName = ""
    @State private var showImport = false
    @State private var importText = ""

    private var bundled: [Preset] { store.bundledPresets() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Banner(text: banner)
                    section(title: "Mitgeliefert (\(bundled.count))", items: bundled, custom: false)
                    section(title: "Eigene (\(customs.count))", items: customs, custom: true)
                    SecBtn(title: "Aktuellen Status als Preset speichern", icon: "plus") {
                        saveName = ""
                        showSave = true
                    }
                    SecBtn(title: "Preset aus Text importieren", icon: "square.and.arrow.down") {
                        importText = ""
                        showImport = true
                    }
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Presets")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { reload() }
            .alert("Preset speichern", isPresented: $showSave) {
                TextField("Name", text: $saveName)
                Button("Speichern") { saveCurrent() }
                Button("Abbrechen", role: .cancel) {}
            }
            .sheet(isPresented: $showImport) {
                NavigationStack {
                    VStack(spacing: 12) {
                        TextEditor(text: $importText)
                            .font(.system(.body, design: .monospaced))
                            .foregroundColor(.fPrimary)
                            .padding(8)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.fCard2))
                            .frame(minHeight: 220)
                        PriBtn(title: "Importieren", icon: "square.and.arrow.down") { doImport() }
                        Spacer()
                    }
                    .padding()
                    .background(Color.fBg)
                    .navigationTitle("Preset importieren")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Schließen") { showImport = false }
                        }
                    }
                }
                .preferredColorScheme(.dark)
            }
        }
    }

    private func section(title: String, items: [Preset], custom: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
                .foregroundColor(.white)
            if items.isEmpty {
                Text(custom ? "Noch keine — speichere deinen Status als Preset." : "—")
                    .font(.subheadline)
                    .foregroundColor(.fTertiary)
                    .padding(8)
            }
            ForEach(items.indices, id: \.self) { i in
                let p = items[i]
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.fCard2)
                            .frame(width: 44, height: 44)
                        if !p.icon.isEmpty, let img = Actions.localImage(p.icon) {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 44, height: 44)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        } else {
                            Image(systemName: "bolt.fill")
                                .foregroundColor(.fCyan)
                        }
                    }
                    Button {
                        Actions.applyPreset(p, notify: show)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.title)
                                .bold()
                                .foregroundColor(.fPrimary)
                            Text(p.act.name)
                                .font(.caption)
                                .foregroundColor(.fTertiary)
                        }
                        Spacer()
                    }
                    if custom {
                        ShareLink(item: presetJson(p)) {
                            Image(systemName: "square.and.arrow.up")
                                .foregroundColor(.fCyan)
                        }
                        Button {
                            store.deleteCustomPreset(name: p.file)
                            reload()
                            show("🗑️ Preset '\(p.title)' gelöscht")
                        } label: {
                            Image(systemName: "trash")
                                .foregroundColor(.fDanger)
                        }
                    } else {
                        Image(systemName: "chevron.right")
                            .foregroundColor(.fTertiary)
                    }
                }
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.fStroke, lineWidth: 1))
            }
        }
    }

    private func presetJson(_ p: Preset) -> String {
        return jsonString(["status": p.status, "application_id": p.appId, "activity": p.act.toDict()])
    }

    private func reload() {
        customs = store.customPresets()
    }

    private func saveCurrent() {
        let name = saveName.trimmingCharacters(in: .whitespaces)
        if name.isEmpty {
            show("Bitte einen Namen eingeben!")
            return
        }
        store.saveCustomPreset(name: name, status: store.act.status, appId: store.appId, act: store.act)
        reload()
        show("✅ Preset '\(name)' gespeichert!")
    }

    private func doImport() {
        let t = importText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, let o = jsonDict(t) else {
            show("❌ Kein gültiges Preset (JSON erwartet)")
            return
        }
        let (act, warns) = ActConfig.sanitize(o["activity"] as? [String: Any])
        store.act = act
        let st = s(o["status"], "online").lowercased()
        store.act.status = ActConfig.STATUS[st] != nil ? st : "online"
        if validAppId(s(o["application_id"])) { store.appId = s(o["application_id"]) }
        store.saveAct()
        showImport = false
        if RpcManager.shared.isRunning { RpcManager.shared.refresh() }
        show(warns.isEmpty ? "✅ Preset importiert!" : "✅ Importiert: \(warns.joined(separator: " ") )")
    }

    private func show(_ s: String) {
        banner = s
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if banner == s { banner = "" }
        }
    }
}
