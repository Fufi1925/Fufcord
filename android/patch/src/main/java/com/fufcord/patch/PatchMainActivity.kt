/*
 * Fufcord Patch — Startbildschirm mit Anleitung (UI komplett in Code, keine Resources nötig).
 * https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten (MIT-Lizenz).
 */
package com.fufcord.patch

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PatchMainActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0D12"))
            addView(box)
        }

        fun title(t: String) {
            box.addView(TextView(this).apply {
                text = t
                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        fun text(t: String) {
            box.addView(TextView(this).apply {
                text = t
                textSize = 14f
                setTextColor(Color.parseColor("#9AA3B2"))
                setPadding(0, 10, 0, 10)
            })
        }
        fun btn(t: String, url: String) {
            box.addView(Button(this).apply {
                text = t
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                    }
                }
            })
        }

        title("⚡ Fufcord Patch v1.4")
        text("So kommt Fufcord IN deine Discord-App:")
        text(
            "1️⃣ Discord installieren (Play Store)\n" +
                "2️⃣ Fufcord Patch installieren (diese App ✓)\n" +
                "3️⃣ LSPatch Manager installieren (Button unten)\n" +
                "4️⃣ In LSPatch: Discord wählen + Fufcord Patch einbetten → Patchen\n" +
                "5️⃣ Gepatchtes Discord installieren & öffnen\n" +
                "6️⃣ Discord → Einstellungen → ⚡ Fufcord → Token → Start 🎉"
        )
        btn("LSPatch laden (GitHub)", "https://github.com/LSPosed/LSPatch/releases")
        btn("Discord (Play Store)", "https://play.google.com/store/apps/details?id=com.discord")
        btn(
            "Anleitung & Hilfe",
            "https://github.com/Fufi1925/Fufcord/blob/main/android/patch/README-PATCH.md"
        )
        text("⚠️ Hinweis: Gemoddete Apps verstoßen gegen die Discord-Regeln (Bann-Risiko klein, aber vorhanden). Bei Problemen: Gepatchtes Discord deinstallieren und das originale neu installieren — Fufcord-Hauptapp funktioniert immer.")
        setContentView(root)
    }
}
