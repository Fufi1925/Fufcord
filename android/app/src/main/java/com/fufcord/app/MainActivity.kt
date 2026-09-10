/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        if (!prefs.setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        b.btnToggle.setOnClickListener { toggleService() }
        b.btnEdit.setOnClickListener { startActivity(Intent(this, EditorActivity::class.java)) }
        b.btnDoctor.setOnClickListener { startActivity(Intent(this, DoctorActivity::class.java)) }
        b.btnExport.setOnClickListener { exportJson() }
        b.btnImport.setOnClickListener { importDialog() }
        b.btnSettings.setOnClickListener { settingsDialog() }
        try {
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtVersion.text = "v${p.versionName} • Custom Rich Presence"
        } catch (_: Exception) { }
        UpdateChecker.check(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val running = RpcService.isRunning
        b.statusDot.text = if (running) "🟢" else "🔴"
        b.statusText.text = if (running) RpcService.statusText else "Gestoppt — tippe Start!"
        b.btnToggle.text = if (running) "⏹ STOP" else "🚀 START"
        val act = prefs.loadAct()
        PreviewBinder.bind(b.previewCard, act, prefs.safeMode)
        buildPresetRow()
    }

    private fun toggleService() {
        try {
            if (RpcService.isRunning) {
                startService(Intent(this, RpcService::class.java).setAction(RpcService.ACTION_STOP))
            } else {
                if (prefs.token.isEmpty()) {
                    Toast.makeText(this, "Erst Token eintragen! (⚙️ Einstellungen)", Toast.LENGTH_LONG).show()
                    return
                }
                val i = Intent(this, RpcService::class.java).setAction(RpcService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "Starte... (läuft im Hintergrund weiter)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Start blockiert: ${e.message}", Toast.LENGTH_LONG).show()
        }
        handler.postDelayed({ refresh() }, 900)
    }

    // ---------------- Presets ----------------
    private fun buildPresetRow() {
        b.presetRow.removeAllViews()
        val all = prefs.loadBundledPresets() + prefs.loadCustomPresets()
        if (all.isEmpty()) {
            val t = TextView(this)
            t.text = "(keine Presets)"
            t.setTextColor(0xFF8B93B0.toInt())
            b.presetRow.addView(t)
            return
        }
        for (p in all) {
            val wrap = android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_TonalButton)
            val chip = MaterialButton(wrap)
            chip.text = p.title
            chip.isAllCaps = false
            if (p.icon.isNotEmpty()) {
                val res = resources.getIdentifier(p.icon, "drawable", packageName)
                if (res != 0) chip.setIconResource(res)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 16, 0)
            chip.layoutParams = lp
            chip.setOnClickListener { applyPreset(p) }
            if (p.file.isNotEmpty() && prefs.loadCustomPresets().any { it.file == p.file }) {
                chip.setOnLongClickListener { confirmDeletePreset(p); true }
            }
            b.presetRow.addView(chip)
        }
    }

    private fun applyPreset(p: Preset) {
        p.act.status = p.status
        prefs.saveAct(p.act)
        if (RpcService.isRunning) RpcService.refresh(this)
        refresh()
        Toast.makeText(this, "✅ Preset '${p.title}' geladen!", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeletePreset(p: Preset) {
        AlertDialog.Builder(this)
            .setTitle("Preset löschen?")
            .setMessage("'${p.title}' wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                prefs.deleteCustomPreset(p.file)
                refresh()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // ---------------- JSON Export / Import (KI) ----------------
    private fun exportJson() {
        val act = prefs.loadAct()
        val o = JSONObject()
            .put("status", act.status)
            .put("application_id", prefs.appId)
            .put("activity", act.toJson())
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, o.toString(2))
        startActivity(Intent.createChooser(send, "Presence-JSON teilen (z.B. an KI-App)"))
    }

    private fun importDialog() {
        val et = EditText(this)
        et.hint = "JSON-Code hier einfügen (z.B. von KI)..."
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 8
        et.typeface = Typeface.MONOSPACE
        et.textSize = 12f
        val scroll = ScrollView(this)
        scroll.addView(et)
        AlertDialog.Builder(this)
            .setTitle("📥 JSON einfügen")
            .setView(scroll)
            .setPositiveButton("Importieren") { _, _ -> doImport(et.text.toString()) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun doImport(text: String) {
        val block = extractJson(text)
        if (block == null) {
            Toast.makeText(this, "❌ Kein JSON gefunden!", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val data = JSONObject(block)
            val actRaw: JSONObject? = if (data.optJSONObject("activity") != null) {
                val st = data.optString("status", "online").lowercase()
                val id = data.optString("application_id", "").trim()
                if (id.isNotEmpty()) {
                    if (validAppId(id)) prefs.appId = id
                    else Toast.makeText(this, "⚠️ App-ID ungültig → alte behalten", Toast.LENGTH_LONG).show()
                }
                if (ActConfig.STATUS.containsKey(st)) {
                    val tmp = data.optJSONObject("activity")!!
                    tmp.put("status", st)
                    tmp
                } else data.optJSONObject("activity")
            } else if (data.has("name")) {
                data
            } else {
                Toast.makeText(this, "❌ Kein 'activity'/'name' gefunden!", Toast.LENGTH_LONG).show()
                return
            }
            val (clean, warns) = ActConfig.sanitize(actRaw)
            prefs.saveAct(clean)
            refresh()
            val msg = StringBuilder("✅ Importiert!")
            for (w in warns.take(3)) msg.append("\n• $w")
            msg.append("\n\nAls Preset speichern? (Editor → Menü)")
            Toast.makeText(this, msg.toString(), Toast.LENGTH_LONG).show()
            askSavePreset(clean)
        } catch (e: Exception) {
            Toast.makeText(this, "❌ JSON fehlerhaft: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askSavePreset(act: ActConfig) {
        val et = EditText(this)
        et.hint = "Preset-Name (leer = nicht speichern)"
        AlertDialog.Builder(this)
            .setTitle("Als Preset speichern?")
            .setView(et)
            .setPositiveButton("Speichern") { _, _ ->
                val n = et.text.toString().trim().lowercase()
                    .replace(Regex("[^a-z0-9-_ ]"), "").replace(" ", "-")
                if (n.isNotEmpty()) {
                    prefs.saveCustomPreset(n, act.status, prefs.appId, act)
                    refresh()
                    Toast.makeText(this, "✅ Preset '$n' gespeichert!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Nein", null)
            .show()
    }

    private fun extractJson(text: String): String? {
        val t = text.replace("```json", "").replace("```", "")
        val start = t.indexOf("{")
        if (start == -1) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until t.length) {
            val ch = t[i]
            if (inStr) {
                if (esc) esc = false
                else if (ch == '\\') esc = true
                else if (ch == '"') inStr = false
            } else {
                if (ch == '"') inStr = true
                else if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) return t.substring(start, i + 1)
                }
            }
        }
        return null
    }

    // ---------------- Einstellungen ----------------
    private fun settingsDialog() {
        val lay = LinearLayout(this)
        lay.orientation = LinearLayout.VERTICAL
        lay.setPadding(48, 24, 48, 24)
        fun label(s: String): TextView {
            val t = TextView(this)
            t.text = s
            t.setTextColor(0xFF8B93B0.toInt())
            lay.addView(t)
            return t
        }
        label("🔑 User-Token (verschlüsselt gespeichert)")
        val etTok = EditText(this)
        etTok.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        etTok.setText(prefs.token)
        etTok.hint = "Token einfügen..."
        lay.addView(etTok)
        label("🆔 Application ID")
        val etApp = EditText(this)
        etApp.inputType = InputType.TYPE_CLASS_NUMBER
        etApp.setText(prefs.appId)
        etApp.hint = "z.B. 123456789012345678"
        lay.addView(etApp)
        val swSafe = SwitchMaterial(this)
        swSafe.text = "🛡️ Sicher-Modus (nur Text, geht immer)"
        swSafe.isChecked = prefs.safeMode
        lay.addView(swSafe)
        val swAuto = SwitchMaterial(this)
        swAuto.text = "🔄 Autostart nach Handy-Neustart"
        swAuto.isChecked = prefs.autostart
        lay.addView(swAuto)

        AlertDialog.Builder(this)
            .setTitle("⚙️ Einstellungen")
            .setView(lay)
            .setPositiveButton("Speichern") { _, _ ->
                val t = etTok.text.toString().trim().replace(" ", "")
                if (t.isNotEmpty()) prefs.token = t
                prefs.appId = etApp.text.toString().trim()
                prefs.safeMode = swSafe.isChecked
                prefs.autostart = swAuto.isChecked
                refresh()
                Toast.makeText(this, "✅ Gespeichert!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
