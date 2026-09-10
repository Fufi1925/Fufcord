/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityDoctorBinding
import com.fufcord.app.databinding.ItemDoctorRowBinding

/** 🩺 Doktor: prüft Token/App/Bilder bei Discord + Auto-Reparatur. */
class DoctorActivity : AppCompatActivity() {

    companion object {
        private const val OK = 0
        private const val WARN = 1
        private const val ERROR = 2
    }

    private lateinit var b: ActivityDoctorBinding
    private lateinit var prefs: PrefsManager
    private var missingAssets = setOf<String>()
    private var appMissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDoctorBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.toolbarBack.setOnClickListener { AnimUtils.finish(this) }
        b.btnFixAll.setOnClickListener { fixAll() }
        b.btnNoButtons.setOnClickListener {
            val a = prefs.loadAct()
            a.buttons.clear()
            prefs.saveAct(a)
            afterLocalFix("✅ Buttons entfernt!")
        }
        b.btnNoImages.setOnClickListener {
            val a = prefs.loadAct()
            a.largeImage = ""; a.largeText = ""; a.smallImage = ""; a.smallText = ""
            prefs.saveAct(a)
            afterLocalFix("✅ Bilder entfernt!")
        }
        b.btnSafeToggle.setOnClickListener {
            prefs.safeMode = !prefs.safeMode
            updateSafeBtn()
            if (RpcService.isRunning) RpcService.refresh(this)
            Toast.makeText(this,
                if (prefs.safeMode) "🛡️ Sicher-Modus AN (nur Text)" else "Sicher-Modus AUS (voll)",
                Toast.LENGTH_SHORT).show()
        }
        b.btnClose.setOnClickListener { AnimUtils.finish(this) }
        updateSafeBtn()
        runChecks()
    }

    private fun afterLocalFix(msg: String) {
        if (RpcService.isRunning) RpcService.refresh(this)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        runChecks()
    }

    private fun updateSafeBtn() {
        b.btnSafeToggle.text = if (prefs.safeMode) "🛡️ Sicher-Modus: AN — ausschalten?"
        else "🛡️ Sicher-Modus einschalten"
    }

    private fun row(type: Int, txt: String) {
        val r = ItemDoctorRowBinding.inflate(LayoutInflater.from(this), b.resultList, false)
        when (type) {
            OK -> {
                r.rowBadge.setBackgroundResource(R.drawable.badge_success)
                r.rowIcon.setImageResource(R.drawable.ic_check)
                r.rowIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.success))
            }
            WARN -> {
                r.rowBadge.setBackgroundResource(R.drawable.badge_warning)
                r.rowIcon.setImageResource(R.drawable.ic_warn)
                r.rowIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.warning))
            }
            else -> {
                r.rowBadge.setBackgroundResource(R.drawable.badge_error)
                r.rowIcon.setImageResource(R.drawable.ic_close)
                r.rowIcon.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.danger))
            }
        }
        r.rowText.text = txt
        b.resultList.addView(r.root)
    }

    private fun runChecks() {
        b.resultList.removeAllViews()
        missingAssets = emptySet()
        appMissing = false
        val act = prefs.loadAct()
        val tok = prefs.token
        val tokenSet = tok.isNotEmpty()
        val appId = prefs.appId.trim()
        val appOk = validAppId(appId)

        if (!tokenSet) row(ERROR, "Kein Token! → Start → Zahnrad → Token eintragen.")
        if (appId.isNotEmpty() && !appOk) row(ERROR, "App-ID ungültig ('$appId') — muss lange Zahl sein!")
        if (appId.isEmpty() && (act.largeImage.isNotEmpty() || act.buttons.isNotEmpty()))
            row(WARN, "Bilder/Buttons ohne App-ID → werden weggelassen (nur Text).")
        for ((v, label) in listOf(act.largeImage to "Großes Bild", act.smallImage to "Kleines Bild")) {
            if (v.isEmpty()) continue
            if (!v.matches(Regex("[a-z0-9_]{1,32}")))
                row(ERROR, "$label '$v': ungültiger Name! (nur a-z, 0-9, _)")
        }
        for ((i, btn) in act.buttons.withIndex()) {
            if (ActConfig.hasEmoji(btn.label))
                row(ERROR, "Button ${i + 1}: Emojis BLOCKIEREN die Anzeige!")
            if (!(btn.url.startsWith("https://") || btn.url.startsWith("http://")))
                row(ERROR, "Button ${i + 1}: Link ungültig!")
        }
        if (act.buttons.isNotEmpty())
            row(WARN, "Hinweis: Buttons werden von Discord manchmal ignoriert — im Zweifel entfernen.")

        b.doctorProgress.visibility = View.VISIBLE

        Thread {
            val tokValid: Boolean?
            var user = ""
            if (tokenSet) {
                val (ok, name) = DiscordApi.getMe(tok)
                tokValid = ok
                user = name
            } else tokValid = null
            var appExists: Boolean? = null
            var appName = ""
            var assets: List<Pair<String, String>> = emptyList()
            if (tokValid == true && appOk) {
                val (ok, apps) = DiscordApi.listApps(tok)
                if (ok) {
                    val hit = apps.firstOrNull { it.first == appId }
                    appExists = hit != null
                    appName = hit?.second ?: ""
                    if (appExists == true) {
                        val (ok2, list) = DiscordApi.listAssets(tok, appId)
                        if (ok2) assets = list
                    }
                }
            }
            runOnUiThread {
                b.doctorProgress.visibility = View.GONE
                when (tokValid) {
                    true -> row(OK, "Token GÜLTIG (Account: $user).")
                    false -> row(ERROR, "Token UNGÜLTIG! Neuen holen (TOKEN-HOLEN.md).")
                    null -> if (tokenSet) row(WARN, "Token nicht prüfbar (Internet?).")
                }
                when (appExists) {
                    true -> row(OK, "App existiert: $appName.")
                    false -> {
                        row(ERROR, "App-ID existiert NICHT in deinem Account! (erfunden?)")
                        appMissing = true
                    }
                    null -> if (appOk) row(WARN, "App nicht prüfbar (Internet?).")
                }
                if (appExists == true) {
                    val names = assets.map { it.second }.toSet()
                    val miss = mutableSetOf<String>()
                    for ((v, label) in listOf(act.largeImage to "Großes Bild", act.smallImage to "Kleines Bild")) {
                        if (v.isEmpty()) continue
                        if (v in names) row(OK, "$label '$v': hochgeladen ✅")
                        else {
                            row(ERROR, "$label '$v': NICHT hochgeladen! → Activity unsichtbar!")
                            miss.add(v)
                        }
                    }
                    missingAssets = miss
                }
            }
        }.start()
    }

    private fun fixAll() {
        val (clean, warns) = ActConfig.sanitize(prefs.loadAct().toJson())
        val notes = warns.toMutableList()
        if (appMissing) {
            prefs.appId = ""
            notes.add("App-ID existiert nicht → entfernt.")
        }
        for (m in missingAssets) {
            if (clean.largeImage == m) { clean.largeImage = ""; clean.largeText = "" }
            if (clean.smallImage == m) { clean.smallImage = ""; clean.smallText = "" }
            notes.add("Bild '$m' nicht hochgeladen → entfernt.")
        }
        prefs.saveAct(clean)
        if (RpcService.isRunning) RpcService.refresh(this)
        Toast.makeText(this,
            "✅ Repariert!\n• " + (notes.take(5).joinToString("\n• ").ifEmpty { "alles schon sauber" }),
            Toast.LENGTH_LONG).show()
        runChecks()
    }

    @Deprecated("Alte Back-Animation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
