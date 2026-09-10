/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySettingsBinding

/** Einstellungen: Token, App-ID (je mit Live-Test), Sicher-Modus, Autostart, Version. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.etToken.setText(prefs.token)
        b.etAppId.setText(prefs.appId)
        b.swSafe.isChecked = prefs.safeMode
        b.swAuto.isChecked = prefs.autostart
        try {
            @Suppress("DEPRECATION")
            val v = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            b.txtVersion.text = "v$v"
        } catch (_: Exception) { }

        b.toolbarBack.setOnClickListener { AnimUtils.finish(this) }

        b.btnTestToken.setOnClickListener {
            val t = b.etToken.text.toString().trim().replace(" ", "")
            if (t.length < 20) {
                b.txtTokenResult.text = "❌ Zu kurz!"
                return@setOnClickListener
            }
            b.txtTokenResult.text = "⏳ Prüfe …"
            b.btnTestToken.isEnabled = false
            Thread {
                val (ok, name) = DiscordApi.getMe(t)
                runOnUiThread {
                    b.btnTestToken.isEnabled = true
                    b.txtTokenResult.text = if (ok) "✅ $name" else "❌ Ungültig ($name)"
                }
            }.start()
        }

        b.btnTestApp.setOnClickListener {
            val id = b.etAppId.text.toString().trim()
            val t = currentToken()
            if (!validAppId(id)) {
                b.txtAppResult.text = "❌ Lange Zahl nötig!"
                return@setOnClickListener
            }
            if (t.length < 20) {
                b.txtAppResult.text = "❌ Erst Token oben prüfen!"
                return@setOnClickListener
            }
            b.txtAppResult.text = "⏳ Prüfe …"
            b.btnTestApp.isEnabled = false
            Thread {
                val (ok, apps) = DiscordApi.listApps(t)
                runOnUiThread {
                    b.btnTestApp.isEnabled = true
                    val hit = apps.firstOrNull { it.first == id }
                    b.txtAppResult.text = when {
                        ok && hit != null -> "✅ ${hit.second}"
                        ok -> "❌ Nicht in deinem Account!"
                        else -> "⚠️ Offline — trotzdem speicherbar"
                    }
                }
            }.start()
        }

        b.btnSave.setOnClickListener {
            val t = currentToken()
            if (t.isNotEmpty()) prefs.token = t
            prefs.appId = b.etAppId.text.toString().trim()
            prefs.safeMode = b.swSafe.isChecked
            prefs.autostart = b.swAuto.isChecked
            if (RpcService.isRunning) RpcService.refresh(this)
            Toast.makeText(this, "✅ Gespeichert!", Toast.LENGTH_SHORT).show()
            AnimUtils.finish(this)
        }
    }

    private fun currentToken(): String =
        b.etToken.text.toString().trim().replace(" ", "")

    @Deprecated("Alte Back-Animation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
