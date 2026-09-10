package com.fufcord.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityDoctorBinding

/** 🩺 Doktor: prüft Token/App/Bilder bei Discord + Auto-Reparatur. */
class DoctorActivity : AppCompatActivity() {

    private lateinit var b: ActivityDoctorBinding
    private lateinit var prefs: PrefsManager
    private var missingAssets = setOf<String>()
    private var appMissing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDoctorBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.btnFixAll.setOnClickListener { fixAll() }
        b.btnNoButtons.setOnClickListener {
            val a = prefs.loadAct()
            a.buttons.clear()
            prefs.saveAct(a)
            Toast.makeText(this, "✅ Buttons entfernt!", Toast.LENGTH_SHORT).show()
            runChecks()
        }
        b.btnNoImages.setOnClickListener {
            val a = prefs.loadAct()
            a.largeImage = ""; a.largeText = ""; a.smallImage = ""; a.smallText = ""
            prefs.saveAct(a)
            Toast.makeText(this, "✅ Bilder entfernt!", Toast.LENGTH_SHORT).show()
            runChecks()
        }
        b.btnSafeToggle.setOnClickListener {
            prefs.safeMode = !prefs.safeMode
            updateSafeBtn()
            Toast.makeText(this,
                if (prefs.safeMode) "🛡️ Sicher-Modus AN (nur Text)" else "Sicher-Modus AUS (voll)",
                Toast.LENGTH_SHORT).show()
        }
        b.btnClose.setOnClickListener { finish() }
        updateSafeBtn()
        runChecks()
    }

    private fun updateSafeBtn() {
        b.btnSafeToggle.text = if (prefs.safeMode) "🛡️ Sicher-Modus: AN (ausschalten?)"
        else "🛡️ Sicher-Modus einschalten"
    }

    private fun row(sym: String, txt: String) {
        val t = TextView(this)
        t.text = "$sym $txt"
        t.textSize = 14f
        t.setPadding(0, 8, 0, 8)
        t.setTextColor(0xFFFFFFFF.toInt())
        b.resultList.addView(t)
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

        if (!tokenSet) row("🔴", "Kein Token! → ⚙️ Einstellungen")
        if (appId.isNotEmpty() && !appOk) row("🔴", "App-ID ungültig ('$appId') — muss lange Zahl sein!")
        if (appId.isEmpty() && (act.largeImage.isNotEmpty() || act.buttons.isNotEmpty()))
            row("🟡", "Bilder/Buttons ohne App-ID → werden weggelassen (nur Text).")
        for ((v, label) in listOf(act.largeImage to "Großes Bild", act.smallImage to "Kleines Bild")) {
            if (v.isEmpty()) continue
            if (!v.matches(Regex("[a-z0-9_]{1,32}")))
                row("🔴", "$label '$v': ungültiger Name! (nur a-z, 0-9, _)")
        }
        for ((i, btn) in act.buttons.withIndex()) {
            if (ActConfig.hasEmoji(btn.label))
                row("🔴", "Button ${i + 1}: Emojis BLOCKIEREN die Anzeige!")
            if (!(btn.url.startsWith("https://") || btn.url.startsWith("http://")))
                row("🔴", "Button ${i + 1}: Link ungültig!")
        }
        if (act.buttons.isNotEmpty())
            row("🟡", "Hinweis: Buttons werden von Discord manchmal ignoriert — im Zweifel entfernen.")

        val netRow = TextView(this)
        netRow.text = "🌐 Frage Discord-API..."
        netRow.setTextColor(0xFFB5BAC1.toInt())
        b.resultList.addView(netRow)

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
                netRow.visibility = View.GONE
                when (tokValid) {
                    true -> row("🟢", "Token GÜLTIG (Account: $user).")
                    false -> row("🔴", "Token UNGÜLTIG! Neuen holen (TOKEN-HOLEN.md).")
                    null -> if (tokenSet) row("🟡", "Token nicht prüfbar (Internet?).")
                }
                when (appExists) {
                    true -> row("🟢", "App existiert: $appName.")
                    false -> {
                        row("🔴", "App-ID existiert NICHT in deinem Account! (erfunden?)")
                        appMissing = true
                    }
                    null -> if (appOk) row("🟡", "App nicht prüfbar (Internet?).")
                }
                if (appExists == true) {
                    val names = assets.map { it.second }.toSet()
                    val miss = mutableSetOf<String>()
                    for ((v, label) in listOf(act.largeImage to "Großes Bild", act.smallImage to "Kleines Bild")) {
                        if (v.isEmpty()) continue
                        if (v in names) row("🟢", "$label '$v': hochgeladen ✅")
                        else {
                            row("🔴", "$label '$v': NICHT hochgeladen! → Activity unsichtbar!")
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
        Toast.makeText(this,
            "✅ Repariert!\n• " + (notes.take(5).joinToString("\n• ").ifEmpty { "alles schon sauber" }),
            Toast.LENGTH_LONG).show()
        runChecks()
    }
}
