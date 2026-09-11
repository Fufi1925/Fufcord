/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySetupBinding

/** Setup-Assistent beim ersten Start: Token → App-ID → Fertig. */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)
        showStep(1)

        b.btnTestToken.setOnClickListener {
            val t = b.etToken.text.toString().trim().replace(" ", "")
            if (t.length < 20) {
                b.txtTokenResult.text = "❌ Zu kurz — kompletten Token einfügen!"
                return@setOnClickListener
            }
            b.txtTokenResult.text = "⏳ Prüfe..."
            b.btnTestToken.isEnabled = false
            Thread {
                val (ok, name) = DiscordApi.getMe(t)
                runOnUiThread {
                    b.btnTestToken.isEnabled = true
                    if (ok) {
                        b.txtTokenResult.text = "✅ Gültig! Account: $name"
                        prefs.token = t
                    } else {
                        b.txtTokenResult.text = "❌ Ungültig ($name) — neuen Token holen! (TOKEN-HOLEN.md im Repo)"
                    }
                }
            }.start()
        }
        b.btnNext1.setOnClickListener {
            val t = b.etToken.text.toString().trim().replace(" ", "")
            if (t.length < 20) {
                Toast.makeText(this, "Erst Token eintragen!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.token = t
            showStep(2)
        }

        b.btnTestApp.setOnClickListener {
            val id = b.etAppId.text.toString().trim()
            if (!validAppId(id)) {
                b.txtAppResult.text = "❌ Muss eine lange Zahl sein!"
                return@setOnClickListener
            }
            b.txtAppResult.text = "⏳ Prüfe..."
            b.btnTestApp.isEnabled = false
            Thread {
                val (ok, apps) = DiscordApi.listApps(prefs.token)
                runOnUiThread {
                    b.btnTestApp.isEnabled = true
                    val hit = apps.firstOrNull { it.first == id }
                    if (ok && hit != null) {
                        b.txtAppResult.text = "✅ Gefunden: ${hit.second}"
                        prefs.appId = id
                    } else if (ok) {
                        b.txtAppResult.text = "❌ ID nicht in deinem Account! (discord.com/developers prüfen)"
                    } else {
                        b.txtAppResult.text = "⚠️ Konnte nicht prüfen (Internet?). Trotzdem weiter möglich."
                        prefs.appId = id
                    }
                }
            }.start()
        }
        b.btnNext2.setOnClickListener {
            val id = b.etAppId.text.toString().trim()
            if (id.isNotEmpty()) prefs.appId = id
            showStep(3)
        }
        b.btnSkip2.setOnClickListener { showStep(3) }

        b.btnFinish.setOnClickListener {
            prefs.setupDone = true
            // RPC sofort starten (Token ist eingetragen) ...
            try {
                if (prefs.token.isNotEmpty()) {
                    val i = Intent(this, RpcService::class.java).setAction(RpcService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                    Toast.makeText(this, "RPC startet im Hintergrund...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
            // ... und Akku-Ausnahme erfragen, damit es so bleibt.
            PowerHelper.explainAndRequest(this) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showStep(n: Int) {
        b.step1.visibility = if (n == 1) View.VISIBLE else View.GONE
        b.step2.visibility = if (n == 2) View.VISIBLE else View.GONE
        b.step3.visibility = if (n == 3) View.VISIBLE else View.GONE
        b.txtSetupInfo.text = "Schritt $n von 3"
    }
}
