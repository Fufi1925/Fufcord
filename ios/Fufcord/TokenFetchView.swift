/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI
import WebKit

/// Token automatisch holen: in Discord einloggen → Token wird ausgelesen (Port von Android).
struct TokenFetchView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    @State private var status = "⏳ Bitte in Discord einloggen …"
    @State private var autoTried = false
    @StateObject private var holder = WebHolder()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text(status)
                    .font(.subheadline)
                    .foregroundColor(.fSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.fCard)
                TokenWebView(holder: holder, url: $url)
                PriBtn(title: "Token jetzt auslesen", icon: "key.fill") { extract() }
                    .padding()
            }
            .background(Color.fBg)
            .navigationTitle("Token holen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onChange(of: url) { _, u in
                if looksLoggedIn(u) {
                    status = "✅ Eingeloggt! Lese Token …"
                    if !autoTried {
                        autoTried = true
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 1_500_000_000)
                            extract()
                        }
                    }
                }
            }
            .onDisappear {
                WKWebsiteDataStore.default().removeData(
                    ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                    modifiedSince: Date(timeIntervalSince1970: 0)) {}
            }
        }
    }

    private func looksLoggedIn(_ u: String) -> Bool {
        let l = u.lowercased()
        if !(l.contains("discord.com/channels") || l.contains("discord.com/app")) { return false }
        if l.contains("/login") || l.contains("/register") || l.contains("verify") { return false }
        return true
    }

    private func extract() {
        guard let w = holder.view else {
            status = "⏳ Browser lädt noch … bitte kurz warten."
            return
        }
        status = "⏳ Lese Token …"
        let js = #"(() => {
  try {
    const raw = localStorage.getItem('token');
    if (raw && raw.length > 10) return raw;
    let f = document.body.appendChild(document.createElement('iframe'));
    try {
      const t = f.contentWindow.localStorage.token;
      f.remove();
      if (t && t.length > 10) return t;
    } catch (e) { try { f.remove(); } catch (e2) {} }
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      const v = localStorage.getItem(k);
      if (v && v.length > 20 && (v.includes('.') || v.startsWith('mfa.'))) return v;
    }
    return '';
  } catch (e) { return ''; }
})()"""#
        w.evaluateJavaScript(js) { res, _ in
            DispatchQueue.main.async {
                self.handleExtractResult(res as? String ?? "")
            }
        }
    }

    private func handleExtractResult(_ raw: String) {
            var token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if token.hasPrefix("\"") && token.hasSuffix("\"") && token.count >= 2 {
                token = String(token.dropFirst().dropLast())
            }
            if token.count < 20 {
                status = "❌ Kein Token gefunden — bist du eingeloggt? (discord.com/channels)"
                return
            }
            status = "⏳ Prüfe Token …"
            Task {
                let (ok, name) = await DiscordApi.getMe(token)
                await MainActor.run {
                    if ok {
                        store.token = token
                        dismiss()
                    } else {
                        status = "❌ Token ungültig — bitte neu einloggen."
                    }
                }
            }
        }
}

class WebHolder: ObservableObject {
    weak var view: WKWebView?
}

struct TokenWebView: UIViewRepresentable {
    var holder: WebHolder
    @Binding var url: String

    func makeUIView(context: Context) -> WKWebView {
        let w = WKWebView()
        w.navigationDelegate = context.coordinator
        w.load(URLRequest(url: URL(string: "https://discord.com/login")!))
        return w
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coord {
        Coord(holder: holder, url: $url)
    }

    class Coord: NSObject, WKNavigationDelegate {
        var holder: WebHolder
        var url: Binding<String>

        init(holder: WebHolder, url: Binding<String>) {
            self.holder = holder
            self.url = url
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            holder.view = webView
            url.wrappedValue = webView.url?.absoluteString ?? ""
        }

        func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
            holder.view = webView
        }
    }
}
