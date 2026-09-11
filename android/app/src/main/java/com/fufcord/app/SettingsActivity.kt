/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySettingsBinding

/** Eigener Einstellungs-Tab: Konto, Verhalten, Updates, Über & Daten. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        loadFields()
        try {
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtSetVersion.text = "Installiert: v${p.versionName}"
        } catch (_: Exception) { }

        b.btnSetTestToken.setOnClickListener { testToken() }
        b.btnSetTestApp.setOnClickListener { testApp() }
        b.btnSetUpdate.setOnClickListener { manualUpdate() }
        b.btnSetGitHub.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Fufi1925/Fufcord")))
            } catch (e: Exception) {
                Toast.makeText(this, "Konnte Browser nicht öffnen", Toast.LENGTH_SHORT).show()
            }
        }
        b.btnSetReset.setOnClickListener { confirmReset() }
        b.btnSetSave.setOnClickListener { saveAll() }
        b.btnSetBack.setOnClickListener { finish() }
    }

    private fun loadFields() {
        b.etSetToken.setText(prefs.token)
        b.etSetAppId.setText(prefs.appId)
        b.swSetSafe.isChecked = prefs.safeMode
        b.swSetAuto.isChecked = prefs.autostart
    }

    private fun testToken() {
        val t = b.etSetToken.text.toString().trim().replace(" ", "")
        if (t.length < 20) {
            b.txtSetTokenResult.text = "❌ Zu kurz — kompletten Token einfügen!"
            return
        }
        b.txtSetTokenResult.text = "⏳ Prüfe..."
        b.btnSetTestToken.isEnabled = false
        Thread {
            val (ok, name) = DiscordApi.getMe(t)
            runOnUiThread {
                b.btnSetTestToken.isEnabled = true
                b.txtSetTokenResult.text = if (ok) {
                    "✅ Gültig! Account: $name"
                } else {
                    "❌ Ungültig ($name) — neuen Token holen! (TOKEN-HOLEN.md im Repo)"
                }
            }
        }.start()
    }

    private fun testApp() {
        val id = b.etSetAppId.text.toString().trim()
        if (!validAppId(id)) {
            b.txtSetAppResult.text = "❌ Muss eine lange Zahl sein!"
            return
        }
        b.txtSetAppResult.text = "⏳ Prüfe..."
        b.btnSetTestApp.isEnabled = false
        Thread {
            val (ok, apps) = DiscordApi.listApps(prefs.token)
            runOnUiThread {
                b.btnSetTestApp.isEnabled = true
                val hit = apps.firstOrNull { it.first == id }
                b.txtSetAppResult.text = if (ok && hit != null) {
                    "✅ Gefunden: ${hit.second}"
                } else if (ok) {
                    "❌ ID nicht in deinem Account! (discord.com/developers prüfen)"
                } else {
                    "⚠️ Konnte nicht prüfen (Internet?). Trotzdem speicherbar."
                }
            }
        }.start()
    }

    private fun manualUpdate() {
        b.btnSetUpdate.isEnabled = false
        b.btnSetUpdate.text = "⏳ Suche..."
        UpdateChecker.check(this) { found ->
            b.btnSetUpdate.isEnabled = true
            b.btnSetUpdate.text = "⬆️ Nach Updates suchen"
            if (!found) {
                Toast.makeText(this, "✅ Du hast die neueste Version!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("🗑 Wirklich löschen?")
            .setMessage("Token & App-ID werden gelöscht. Presence & Presets bleiben erhalten.")
            .setPositiveButton("Löschen") { _, _ ->
                prefs.token = ""
                prefs.appId = ""
                prefs.skipVersion = ""
                loadFields()
                b.txtSetTokenResult.text = ""
                b.txtSetAppResult.text = ""
                Toast.makeText(this, "🗑 Gelöscht!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun saveAll() {
        val t = b.etSetToken.text.toString().trim().replace(" ", "")
        if (t.isNotEmpty()) prefs.token = t
        prefs.appId = b.etSetAppId.text.toString().trim()
        val wasSafe = prefs.safeMode
        prefs.safeMode = b.swSetSafe.isChecked
        prefs.autostart = b.swSetAuto.isChecked
        if (RpcService.isRunning && wasSafe != prefs.safeMode) RpcService.refresh(this)
        Toast.makeText(this, "✅ Gespeichert!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
