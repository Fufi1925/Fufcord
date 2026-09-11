/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI
import PhotosUI
import UIKit

/// Studio: Activity bearbeiten, Bilder hochladen, Assets verwalten.
struct EditorView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss

    @State private var didLoad = false
    @State private var name = ""
    @State private var typeSel = 0
    @State private var details = ""
    @State private var state = ""
    @State private var large = ""
    @State private var largeText = ""
    @State private var small = ""
    @State private var smallText = ""
    @State private var streamUrl = ""
    @State private var b1label = ""
    @State private var b1url = ""
    @State private var b2label = ""
    @State private var b2url = ""
    @State private var useTs = true
    @State private var tsMode = 0
    @State private var offsetMin = ""
    @State private var partyCur = ""
    @State private var partyMax = ""
    @State private var statusSel = "online"

    @State private var banner = ""
    @State private var assets: [(String, String)] = []
    @State private var assetsLoading = false
    @State private var pickLarge: PhotosPickerItem?
    @State private var pickSmall: PhotosPickerItem?
    @State private var pendingImage: UIImage?
    @State private var pendingTarget = "large"
    @State private var uploadName = ""
    @State private var showNameAlert = false
    @State private var errDetails = ""
    @State private var showErr = false
    @State private var uploading = false

    private var draft: ActConfig {
        var btns: [ActButton] = []
        if !b1label.trimmingCharacters(in: .whitespaces).isEmpty {
            btns.append(ActButton(label: b1label, url: b1url))
        }
        if !b2label.trimmingCharacters(in: .whitespaces).isEmpty {
            btns.append(ActButton(label: b2label, url: b2url))
        }
        return ActConfig(
            name: name, type: typeSel, details: details, state: state,
            largeImage: large.trimmingCharacters(in: .whitespaces).lowercased(),
            largeText: largeText,
            smallImage: small.trimmingCharacters(in: .whitespaces).lowercased(),
            smallText: smallText,
            streamUrl: streamUrl, buttons: btns, useTimestamp: useTs,
            partyCurrent: Int(partyCur) ?? 0, partyMax: Int(partyMax) ?? 0,
            status: statusSel, timestampMode: tsMode,
            timestampOffsetSec: Int64(Int(offsetMin) ?? 0) * 60
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Banner(text: banner)
                    PreviewCard(act: draft, safe: store.safeMode)
                    card("Aktivität") {
                        field("Anzeigename", $name, hint: "z. B. Spotify / Minecraft")
                        Picker("Typ", selection: $typeSel) {
                            ForEach([0, 1, 2, 3, 4, 5], id: \.self) { t in
                                Text(ActConfig.TYPES[t] ?? "?").tag(t)
                            }
                        }
                        .pickerStyle(.segmented)
                        field("Details (Zeile 1)", $details)
                        field("Status (Zeile 2)", $state)
                        if typeSel == 1 {
                            field("Stream-URL", $streamUrl, hint: "https://twitch.tv/...")
                        }
                        Picker("Online-Status", selection: $statusSel) {
                            ForEach(["online", "idle", "dnd", "invisible"], id: \.self) { st in
                                Text(ActConfig.STATUS[st] ?? st).tag(st)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    card("Bilder") {
                        field("Großes Bild (Asset-Name)", $large, hint: "z. B. spotify")
                        field("Großer Bild-Text", $largeText, hint: "Tooltip beim Darüberfahren")
                        field("Kleines Bild (Asset-Name)", $small)
                        field("Kleiner Bild-Text", $smallText)
                        PhotosPicker(selection: $pickLarge, matching: .images) {
                            uploadLabel("Großes Bild hochladen")
                        }
                        PhotosPicker(selection: $pickSmall, matching: .images) {
                            uploadLabel("Kleines Bild hochladen")
                        }
                        if uploading {
                            Text("⏳ Lädt hoch …")
                                .font(.subheadline)
                                .foregroundColor(.fSecondary)
                        }
                    }
                    card("Hochgeladene Assets (\(assets.count))") {
                        if assetsLoading {
                            Text("⏳ Lädt …").font(.subheadline).foregroundColor(.fSecondary)
                        } else if assets.isEmpty {
                            Text("Noch keine — lade oben ein Bild hoch.")
                                .font(.subheadline)
                                .foregroundColor(.fTertiary)
                        }
                        ForEach(assets.indices, id: \.self) { i in
                            let a = assets[i]
                            HStack(spacing: 10) {
                                AsyncImage(url: URL(string: DiscordApi.appAssetUrl(appId: store.appId, assetId: a.0))) { im in
                                    im.resizable().scaledToFill()
                                } placeholder: {
                                    Text("🖼️")
                                }
                                .frame(width: 40, height: 40)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                Text(a.1)
                                    .font(.subheadline)
                                    .foregroundColor(.fPrimary)
                                    .lineLimit(1)
                                Spacer()
                                Button("Groß") { adopt(a.1, target: "large") }
                                    .font(.caption).bold()
                                    .foregroundColor(.fCyan)
                                Button("Klein") { adopt(a.1, target: "small") }
                                    .font(.caption).bold()
                                    .foregroundColor(.fCyan)
                                Button {
                                    deleteAsset(a)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.caption)
                                        .foregroundColor(.fDanger)
                                }
                            }
                        }
                        SecBtn(title: "Aktualisieren", icon: "arrow.clockwise") { loadAssets() }
                    }
                    card("Buttons (max. 2)") {
                        field("Button 1 – Text", $b1label, hint: "keine Emojis!")
                        field("Button 1 – Link", $b1url, hint: "https://…")
                        field("Button 2 – Text", $b2label)
                        field("Button 2 – Link", $b2url, hint: "https://…")
                    }
                    card("Timer & Party") {
                        Toggle("⏱ Zeit anzeigen", isOn: $useTs)
                            .foregroundColor(.fPrimary)
                        Toggle("Eigener Beginn (Zeit zurückdrehen)", isOn: Binding(
                            get: { tsMode == 1 },
                            set: { tsMode = $0 ? 1 : 0 }
                        ))
                        .foregroundColor(.fPrimary)
                        if tsMode == 1 {
                            field("Minuten zurück", $offsetMin, hint: "z. B. 43200 = 30 Tage")
                                .keyboardType(.numberPad)
                        }
                        HStack {
                            field("Party: Ich", $partyCur).keyboardType(.numberPad)
                            field("Party: Max", $partyMax).keyboardType(.numberPad)
                        }
                    }
                    PriBtn(title: "Speichern", icon: "checkmark") { save() }
                }
                .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Studio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Schließen") { dismiss() }
                }
            }
            .onAppear {
                if !didLoad {
                    didLoad = true
                    let a = store.act
                    name = a.name
                    typeSel = a.type
                    details = a.details
                    state = a.state
                    large = a.largeImage
                    largeText = a.largeText
                    small = a.smallImage
                    smallText = a.smallText
                    streamUrl = a.streamUrl
                    if a.buttons.count > 0 { b1label = a.buttons[0].label; b1url = a.buttons[0].url }
                    if a.buttons.count > 1 { b2label = a.buttons[1].label; b2url = a.buttons[1].url }
                    useTs = a.useTimestamp
                    tsMode = a.timestampMode
                    if a.timestampOffsetSec > 0 { offsetMin = String(a.timestampOffsetSec / 60) }
                    if a.partyCurrent > 0 { partyCur = String(a.partyCurrent) }
                    if a.partyMax > 0 { partyMax = String(a.partyMax) }
                    statusSel = a.status
                    loadAssets()
                }
            }
            .onChange(of: pickLarge) { _, v in handlePick(v, target: "large") }
            .onChange(of: pickSmall) { _, v in handlePick(v, target: "small") }
            .alert("Bild hochladen", isPresented: $showNameAlert) {
                TextField("Name (a-z, 0-9, _)", text: $uploadName)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Button("Hochladen") { doUpload() }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Kurz & klein, z. B. „logo\" — so heißt das Bild in Discord.")
            }
            .alert("Upload fehlgeschlagen", isPresented: $showErr) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errDetails.isEmpty ? "Unbekannter Fehler" : errDetails)
            }
        }
    }

    // MARK: - Bausteine

    private func card<Content: View>(_ title: String, @ViewBuilder _ c: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundColor(.white)
            c()
        }
        .glassCard()
    }

    private func field(_ label: String, _ text: Binding<String>, hint: String = "") -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.fSecondary)
            TextField(hint.isEmpty ? label : hint, text: text)
                .foregroundColor(.fPrimary)
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.fCard2))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.fStroke, lineWidth: 1))
        }
    }

    private func uploadLabel(_ title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "photo").foregroundColor(.fCyan)
            Text(title).bold().foregroundColor(.fPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.fCard2))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.fStroke, lineWidth: 1))
    }

    // MARK: - Logik

    private func save() {
        let (clean, warns) = ActConfig.sanitize(draft.toDict())
        store.act = clean
        store.saveAct()
        if RpcManager.shared.isRunning { RpcManager.shared.refresh() }
        dismiss()
    }

    private func loadAssets() {
        if store.token.isEmpty || !validAppId(store.appId) {
            assets = []
            return
        }
        assetsLoading = true
        Task {
            let (ok, list) = await DiscordApi.listAssets(token: store.token, appId: store.appId)
            await MainActor.run {
                assetsLoading = false
                assets = ok ? list : []
                if !ok { show("⚠️ Assets konnten nicht geladen werden") }
            }
        }
    }

    private func adopt(_ name: String, target: String) {
        var set = store.customAssets
        set.insert(name)
        store.customAssets = set
        if target == "large" { large = name } else { small = name }
        show("✅ '\(name)' als \(target == "large" ? "großes" : "kleines") Bild gesetzt")
    }

    private func deleteAsset(_ a: (String, String)) {
        Task {
            let ok = await DiscordApi.deleteAsset(token: store.token, appId: store.appId, assetId: a.0)
            await MainActor.run {
                if ok {
                    var set = store.customAssets
                    set.remove(a.1)
                    store.customAssets = set
                    show("🗑️ '\(a.1)' gelöscht")
                    loadAssets()
                } else {
                    show("❌ Löschen fehlgeschlagen")
                }
            }
        }
    }

    private func handlePick(_ item: PhotosPickerItem?, target: String) {
        guard let item = item else { return }
        if target == "large" { pickLarge = nil } else { pickSmall = nil }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let ui = UIImage(data: data) else {
                await MainActor.run { show("❌ Bild konnte nicht gelesen werden") }
                return
            }
            await MainActor.run {
                pendingImage = ui
                pendingTarget = target
                uploadName = target == "large" ? large : small
                showNameAlert = true
            }
        }
    }

    private func doUpload() {
        guard let ui = pendingImage else { return }
        let nm = uploadName.trimmingCharacters(in: .whitespaces).lowercased()
        if nm.range(of: "^[a-z0-9_]{1,32}$", options: .regularExpression) == nil {
            show("❌ Nur a-z, 0-9 und _ (max. 32)!")
            return
        }
        if store.token.isEmpty || !validAppId(store.appId) {
            show("❌ Erst Token + App-ID eintragen! (Einstellungen)")
            return
        }
        uploading = true
        Task {
            guard let (data, mime) = Actions.compressForDiscord(ui) else {
                await MainActor.run {
                    uploading = false
                    show("❌ Bild zu groß / defekt")
                }
                return
            }
            let url = "data:\(mime);base64," + data.base64EncodedString()
            let (ok, res) = await DiscordApi.uploadAsset(token: store.token, appId: store.appId,
                                                         name: nm, imageDataUrl: url)
            await MainActor.run {
                uploading = false
                if ok {
                    var set = store.customAssets
                    set.insert(nm)
                    store.customAssets = set
                    if pendingTarget == "large" { large = nm } else { small = nm }
                    show("✅ '\(nm)' hochgeladen! (~5 Min warten)")
                    loadAssets()
                } else {
                    if res.contains("HTTP 400") || res.contains("HTTP 404") {
                        errDetails = res
                        showErr = true
                    }
                    show(String("❌ Upload: \(res)".prefix(150)))
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
