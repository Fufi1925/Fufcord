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
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityMainBinding
import com.fufcord.app.databinding.ItemPresetBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var firstShow = true

    // FIX: Status lebt — aktualisiert sich auch bei „Neuversuch in Xs" etc.
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        if (!prefs.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
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
        b.btnEdit.setOnClickListener { AnimUtils.launch(this, EditorActivity::class.java) }
        b.btnDoctor.setOnClickListener { AnimUtils.launch(this, DoctorActivity::class.java) }
        b.btnExport.setOnClickListener { exportJson() }
        b.btnImport.setOnClickListener { importDialog() }
        b.btnSettings.setOnClickListener { AnimUtils.launch(this, SettingsActivity::class.java) }
        try {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtVersion.text = "v${p.versionName}"
        } catch (_: Exception) { }
        UpdateChecker.check(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.postDelayed(ticker, 2500)
        if (firstShow) {
            firstShow = false
            AnimUtils.stagger(b.contentRoot)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        AnimUtils.stopPulse(b.statusDot)
    }

    private fun refresh() {
        val running = RpcService.isRunning
        if (running) {
            b.statusCard.setBackgroundResource(R.drawable.status_card_on)
            b.statusDot.setBackgroundResource(R.drawable.dot_on)
            AnimUtils.startPulse(b.statusDot)
            b.statusText.text = RpcService.statusText.ifEmpty { "Online" }
            val sub = RpcService.activityName.ifEmpty { "Verbunden mit Discord" }
            b.statusSub.text = sub
            b.btnToggle.text = getString(R.string.main_stop)
            b.btnToggle.setIconResource(R.drawable.ic_stop)
            b.btnToggle.setBackgroundResource(R.drawable.btn_stop)
        } else {
            AnimUtils.stopPulse(b.statusDot)
            b.statusCard.setBackgroundResource(R.drawable.status_card_off)
            b.statusDot.setBackgroundResource(R.drawable.dot_off)
            b.statusText.text = getString(R.string.main_status_stopped)
            b.statusSub.text = getString(R.string.main_status_hint)
            b.btnToggle.text = getString(R.string.main_start)
            b.btnToggle.setIconResource(R.drawable.ic_play)
            b.btnToggle.setBackgroundResource(R.drawable.btn_primary)
        }
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
                    Toast.makeText(this, "Erst Token eintragen! (Zahnrad oben rechts)", Toast.LENGTH_LONG).show()
                    AnimUtils.launch(this, SettingsActivity::class.java)
                    return
                }
                val i = Intent(this, RpcService::class.java).setAction(RpcService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "Starte … (läuft im Hintergrund weiter)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Start blockiert: ${e.message}", Toast.LENGTH_LONG).show()
        }
        handler.postDelayed({ refresh() }, 900)
    }

    // ---------------- Presets ----------------
    private fun buildPresetRow() {
        b.presetRow.removeAllViews()
        // FIX: Custom-Presets nur EINMAL laden (vorher pro Preset neu vom Datenträger).
        val custom = prefs.loadCustomPresets()
        val customFiles = custom.map { it.file }.toSet()
        val all = prefs.loadBundledPresets() + custom
        if (all.isEmpty()) {
            val t = TextView(this)
            t.text = "(keine Presets)"
            t.setTextColor(getColor(R.color.text_tertiary))
            b.presetRow.addView(t)
            return
        }
        for (p in all) {
            val item = ItemPresetBinding.inflate(LayoutInflater.from(this), b.presetRow, false)
            item.presetTitle.text = p.title
            if (p.icon.isNotEmpty()) {
                val res = resources.getIdentifier(p.icon, "drawable", packageName)
                if (res != 0) {
                    item.presetIcon.setImageResource(res)
                    item.presetIcon.visibility = View.VISIBLE
                    item.presetLetter.visibility = View.GONE
                } else {
                    showLetter(item, p.title)
                }
            } else {
                showLetter(item, p.title)
            }
            item.root.setOnClickListener { applyPreset(p) }
            if (p.file in customFiles) {
                item.root.setOnLongClickListener { confirmDeletePreset(p); true }
            }
            b.presetRow.addView(item.root)
        }
    }

    private fun showLetter(item: ItemPresetBinding, title: String) {
        item.presetIcon.visibility = View.GONE
        item.presetLetter.visibility = View.VISIBLE
        item.presetLetter.text = title.trim().firstOrNull()?.uppercase() ?: "★"
    }

    private fun applyPreset(p: Preset) {
        p.act.status = p.status
        prefs.saveAct(p.act)
        // FIX: Custom-Preset mit eigener App-ID? → übernehmen (wie in Termux-Version).
        if (p.appId.isNotEmpty() && validAppId(p.appId)) {
            prefs.appId = p.appId
        }
        if (RpcService.isRunning) RpcService.refresh(this)
        refresh()
        Toast.makeText(this, "✅ Preset '${p.title}' geladen!", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeletePreset(p: Preset) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.preset_delete_title))
            .setMessage("'${p.title}' wirklich löschen?")
            .setPositiveButton(getString(R.string.dlg_delete)) { _, _ ->
                prefs.deleteCustomPreset(p.file)
                refresh()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
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
        startActivity(Intent.createChooser(send, getString(R.string.import_share_title)))
    }

    private fun importDialog() {
        val et = EditText(this)
        et.hint = getString(R.string.import_hint)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 8
        et.typeface = Typeface.MONOSPACE
        et.textSize = 12f
        val scroll = ScrollView(this)
        scroll.addView(et)
        scroll.setPadding(48, 24, 48, 0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.import_go)) { _, _ -> doImport(et.text.toString()) }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
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
            // FIX: Status nur ändern, wenn das JSON wirklich einen enthält —
            // sonst bleibt der bisherige erhalten.
            val hadStatus = data.has("status")
            val actRaw: JSONObject? = if (data.optJSONObject("activity") != null) {
                val st = data.optString("status", "online").lowercase()
                val id = data.optString("application_id", "").trim()
                if (id.isNotEmpty()) {
                    if (validAppId(id)) prefs.appId = id
                    else Toast.makeText(this, "⚠️ App-ID ungültig → alte behalten", Toast.LENGTH_LONG).show()
                }
                if (hadStatus && ActConfig.STATUS.containsKey(st)) {
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
            if (!hadStatus) clean.status = prefs.loadAct().status
            prefs.saveAct(clean)
            if (RpcService.isRunning) RpcService.refresh(this)
            refresh()
            val msg = StringBuilder("✅ Importiert!")
            for (w in warns.take(3)) msg.append("\n• $w")
            Toast.makeText(this, msg.toString(), Toast.LENGTH_LONG).show()
            askSavePreset(clean)
        } catch (e: Exception) {
            Toast.makeText(this, "❌ JSON fehlerhaft: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askSavePreset(act: ActConfig) {
        val et = EditText(this)
        et.hint = getString(R.string.preset_save_hint)
        et.setPadding(48, 24, 48, 24)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.preset_save_title))
            .setView(et)
            .setPositiveButton(getString(R.string.dlg_save)) { _, _ ->
                val n = et.text.toString().trim().lowercase()
                    .replace(Regex("[^a-z0-9-_ ]"), "").replace(" ", "-")
                if (n.isNotEmpty()) {
                    prefs.saveCustomPreset(n, act.status, prefs.appId, act)
                    refresh()
                    Toast.makeText(this, "✅ Preset '$n' gespeichert!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dlg_no), null)
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
}
