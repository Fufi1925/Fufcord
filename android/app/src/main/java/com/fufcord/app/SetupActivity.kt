/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySetupBinding

/** Setup-Assistent beim ersten Start: Token → App-ID → Fertig. */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private lateinit var prefs: PrefsManager
    private var step = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)
        showStep(1, animate = false)

        b.btnTestToken.setOnClickListener {
            val t = b.etToken.text.toString().trim().replace(" ", "")
            if (t.length < 20) {
                b.txtTokenResult.text = "❌ Zu kurz — kompletten Token einfügen!"
                return@setOnClickListener
            }
            b.txtTokenResult.text = "⏳ Prüfe …"
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
        b.btnAutoToken.setOnClickListener {
            startActivity(Intent(this, TokenFetchActivity::class.java))
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
            b.txtAppResult.text = "⏳ Prüfe …"
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
            // FIX: ungültige (nicht-leere) App-ID nicht still übernehmen.
            if (id.isNotEmpty() && !validAppId(id)) {
                Toast.makeText(this, "App-ID ungültig — prüfen oder überspringen!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (id.isNotEmpty()) prefs.appId = id
            showStep(3)
        }
        b.btnSkip2.setOnClickListener { showStep(3) }

        b.btnFinish.setOnClickListener {
            prefs.setupDone = true
            val next = if (prefs.permissionsDone) MainActivity::class.java else PermissionActivity::class.java
            startActivity(Intent(this, next))
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        // Einmalig beim Einrichten: Token schon da? Sonst automatisch holen anbieten.
        if (prefs.token.isEmpty() && !prefs.tokenAsked) askToken()
    }

    override fun onResume() {
        super.onResume()
        // Rückkehr vom automatischen Holen: Feld füllen.
        if (b.etToken.text.isNullOrEmpty() && prefs.token.isNotEmpty()) {
            b.etToken.setText(prefs.token)
            b.txtTokenResult.text = getString(R.string.fetch_fetched)
        }
    }

    /** „Hast du schon einen Token?" → Nein → automatisch holen anbieten. */
    private fun askToken() {
        prefs.tokenAsked = true
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_token_q_title))
            .setMessage(getString(R.string.setup_token_q_text))
            .setPositiveButton(getString(R.string.setup_token_q_yes), null)
            .setNegativeButton(getString(R.string.setup_token_q_no)) { _, _ -> askAutofetch() }
            .setCancelable(true)
            .show()
    }

    private fun askAutofetch() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_autofetch_title))
            .setMessage(getString(R.string.setup_autofetch_text))
            .setPositiveButton(getString(R.string.setup_autofetch_yes)) { _, _ ->
                startActivity(Intent(this, TokenFetchActivity::class.java))
            }
            .setNegativeButton(getString(R.string.setup_autofetch_no), null)
            .setCancelable(true)
            .show()
    }

    private fun showStep(n: Int, animate: Boolean = true) {
        step = n
        val views = listOf(b.step1, b.step2, b.step3)
        views.forEachIndexed { i, v ->
            if (i == n - 1) {
                if (animate) AnimUtils.fadeSlideIn(v) else v.visibility = View.VISIBLE
            } else {
                v.visibility = View.GONE
            }
        }
        b.txtSetupInfo.text = getString(R.string.setup_step, n)
        b.setupProgress.setProgress(n * 100 / 3, animate)
    }

    @Deprecated("Zurück = eine Stufe hoch")
    override fun onBackPressed() {
        if (step > 1) showStep(step - 1)
        else super.onBackPressed()
    }
}
