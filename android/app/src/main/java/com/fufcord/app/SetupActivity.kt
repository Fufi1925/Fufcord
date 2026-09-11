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
import android.app.Dialog
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySetupBinding
import com.fufcord.app.databinding.DialogAppAutoBinding
import com.fufcord.app.databinding.DialogTokenAskBinding

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
        b.btnAutoAppId.setOnClickListener { showAppAuto() }
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

    /** „Hast du schon einen Token?" → Nein → automatisch holen anbieten. (Custom-Popup) */
    private fun askToken() {
        prefs.tokenAsked = true
        showTokenAsk(
            getString(R.string.setup_token_q_title),
            getString(R.string.setup_token_q_text),
            getString(R.string.setup_token_q_yes),
            getString(R.string.setup_token_q_no),
            onYes = {},
            onNo = { askAutofetch() }
        )
    }

    private fun askAutofetch() {
        showTokenAsk(
            getString(R.string.setup_autofetch_title),
            getString(R.string.setup_autofetch_text),
            getString(R.string.setup_autofetch_yes),
            getString(R.string.setup_autofetch_no),
            onYes = { startActivity(Intent(this, TokenFetchActivity::class.java)) }
        )
    }

    /** Wiederverwendbares Ja/Nein-Pop-up im Glas-Stil mit Blur. */
    private fun showTokenAsk(title: String, text: String, yes: String, no: String,
                             onYes: () -> Unit, onNo: (() -> Unit)? = null) {
        val d = DialogTokenAskBinding.inflate(layoutInflater)
        val dlg = Dialog(this, R.style.Theme_Fufcord_Dialog)
        dlg.setContentView(d.root)
        d.txtTqTitle.text = title
        d.txtTqText.text = text
        d.btnTqYes.text = yes
        d.btnTqNo.text = no
        d.btnTqYes.setOnClickListener { dlg.dismiss(); onYes() }
        d.btnTqNo.setOnClickListener { dlg.dismiss(); onNo?.invoke() }
        dlg.show()
        val wm = resources.displayMetrics
        dlg.window?.setLayout((wm.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        DialogUtils.blurBehind(dlg)
    }

    /** App-ID automatisch: vorhandene App antippen oder neue erstellen. */
    private fun showAppAuto() {
        val token = prefs.token.ifEmpty { b.etToken.text.toString().trim().replace(" ", "") }
        val d = DialogAppAutoBinding.inflate(layoutInflater)
        val dlg = Dialog(this, R.style.Theme_Fufcord_Dialog)
        dlg.setContentView(d.root)
        d.btnAppCancel.setOnClickListener { dlg.dismiss() }
        if (token.length < 20) {
            d.txtAppStatus.text = getString(R.string.app_need_token)
        } else {
            d.txtAppStatus.text = getString(R.string.app_loading)
            Thread {
                val (ok, apps) = DiscordApi.listApps(token)
                runOnUiThread {
                    if (!ok) {
                        d.txtAppStatus.text = getString(R.string.app_fail, "Token & Internet prüfen")
                        return@runOnUiThread
                    }
                    if (apps.isEmpty()) {
                        d.txtAppStatus.text = getString(R.string.app_none)
                        return@runOnUiThread
                    }
                    d.txtAppStatus.text = getString(R.string.app_tap)
                    val dens = resources.displayMetrics.density
                    for ((id, name) in apps.reversed()) {
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.chip_bg)
                            isClickable = true
                            isFocusable = true
                            val pad = (12 * dens).toInt()
                            setPadding((14 * dens).toInt(), pad, (14 * dens).toInt(), pad)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = (8 * dens).toInt() }
                        }
                        val tvName = TextView(this).apply {
                            text = name.ifEmpty { "(ohne Namen)" }
                            setTextColor(getColor(R.color.text_primary))
                            textSize = 15f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val tvId = TextView(this).apply {
                            text = id
                            setTextColor(getColor(R.color.text_tertiary))
                            textSize = 12f
                        }
                        row.addView(tvName)
                        row.addView(tvId)
                        row.setOnClickListener {
                            prefs.appId = id
                            b.etAppId.setText(id)
                            b.txtAppResult.text = "✅ $name"
                            Toast.makeText(this, getString(R.string.app_picked), Toast.LENGTH_SHORT).show()
                            dlg.dismiss()
                        }
                        d.layAppList.addView(row)
                    }
                }
            }.start()
        }
        d.btnAppCreate.setOnClickListener {
            val name = d.etAppName.text.toString().trim()
            if (name.length < 2) {
                Toast.makeText(this, getString(R.string.app_need_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (token.length < 20) {
                d.txtAppStatus.text = getString(R.string.app_need_token)
                return@setOnClickListener
            }
            d.txtAppStatus.text = getString(R.string.app_creating)
            d.btnAppCreate.isEnabled = false
            Thread {
                val (ok2, res2) = DiscordApi.createApp(token, name)
                runOnUiThread {
                    d.btnAppCreate.isEnabled = true
                    if (ok2) {
                        prefs.appId = res2
                        b.etAppId.setText(res2)
                        b.txtAppResult.text = "✅ $name"
                        Toast.makeText(this, getString(R.string.app_created), Toast.LENGTH_LONG).show()
                        dlg.dismiss()
                    } else {
                        d.txtAppStatus.text = getString(R.string.app_create_fail, res2.take(150))
                    }
                }
            }.start()
        }
        dlg.show()
        val wm = resources.displayMetrics
        dlg.window?.setLayout((wm.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        DialogUtils.blurBehind(dlg)
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
