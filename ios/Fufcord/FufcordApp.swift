/*
 * Fufcord iOS — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

import SwiftUI

@main
struct FufcordApp: App {
    @StateObject private var store = Store.shared
    @StateObject private var rpc = RpcManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(rpc)
                .preferredColorScheme(.dark)
        }
    }
}
