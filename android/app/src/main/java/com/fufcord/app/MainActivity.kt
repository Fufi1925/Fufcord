/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Base64
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.app.Dialog
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityMainBinding
import com.fufcord.app.databinding.DialogImportBinding
import com.fufcord.app.databinding.DialogDiscordBinding
import com.fufcord.app.databinding.ItemPresetRowBinding
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var firstShow = true
    private var currentTab = 0

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
        if (!prefs.permissionsDone) {
            startActivity(Intent(this, PermissionActivity::class.java))
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
        b.rowStudio.setOnClickListener { AnimUtils.launch(this, EditorActivity::class.java) }
        b.rowImport.setOnClickListener { importDialog() }
        b.rowExport.setOnClickListener { exportJson() }
        b.rowPerms.setOnClickListener { AnimUtils.launch(this, PermissionActivity::class.java) }
        b.rowUpdate.setOnClickListener {
            prefs.skipVersion = ""
            UpdateChecker.check(this)
            Toast.makeText(this, R.string.help_updating, Toast.LENGTH_SHORT).show()
        }
        b.rowHelp.setOnClickListener { AnimUtils.launch(this, HelpActivity::class.java) }
        b.rowDiscord.setOnClickListener { openUrl("https://discord.gg/8EzjRTksJP") }
        b.rowShare.setOnClickListener { shareApp() }
        try {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtVersion.text = "v${p.versionName}"
        } catch (_: Exception) { }
        try {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtMoreVersion.text = "Fufcord v${p.versionName}"
        } catch (_: Exception) { }
        try {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtCredVersion.text = "v${p.versionName}"
        } catch (_: Exception) { }
        UpdateChecker.check(this)
        currentTab = savedInstanceState?.getInt("tab", 0) ?: 0
        selectTab(currentTab, false)
        b.bottomNav.setOnItemSelectedListener {
            if (it.itemId == R.id.tab_settings) {
                AnimUtils.launch(this, SettingsActivity::class.java)
                return@setOnItemSelectedListener false
            }
            val i = tabIndex(it.itemId)
            if (i != currentTab) selectTab(i, true)
            true
        }
        b.btnCredDiscord.setOnClickListener { openUrl("https://discord.gg/8EzjRTksJP") }
        b.btnCredGithub.setOnClickListener { openUrl("https://github.com/Fufi1925/Fufcord") }
        b.txtCredId.setOnClickListener {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Discord-ID", "1303627964734246944"))
                Toast.makeText(this, getString(R.string.credits_copied), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { }
        }
        loadCredits()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.postDelayed(ticker, 2500)
        if (firstShow) {
            firstShow = false
            AnimUtils.stagger(b.overviewInner)
            b.bottomNav.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
            if (!prefs.discordPromoSeen) discordDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        AnimUtils.stopPulse(b.statusDot)
    }

    private fun tabIndex(id: Int) = when (id) {
        R.id.tab_presets -> 1
        R.id.tab_more -> 2
        R.id.tab_credits -> 3
        else -> 0
    }

    /** Tab wechseln mit Hochblend-Animation. */
    private fun selectTab(index: Int, animate: Boolean) {
        currentTab = index
        val tabs = listOf(b.tabOverview, b.tabPresets, b.tabMore, b.tabCredits)
        tabs.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        b.bottomNav.menu.getItem(index).isChecked = true
        if (animate) {
            val v = tabs[index]
            v.alpha = 0f
            v.translationY = 28f
            v.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** Discord-Community-Popup (einmalig nach Setup). */
    private fun discordDialog() {
        val dc = DialogDiscordBinding.inflate(layoutInflater)
        val dlg = Dialog(this, R.style.Theme_Fufcord_Dialog)
        dlg.setContentView(dc.root)
        dlg.setCanceledOnTouchOutside(false)
        dc.btnDiscordYes.setOnClickListener {
            prefs.discordPromoSeen = true
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/8EzjRTksJP")))
            } catch (e: Exception) { }
            dlg.dismiss()
        }
        dc.btnDiscordNo.setOnClickListener { prefs.discordPromoSeen = true; dlg.dismiss() }
        dlg.show()
        val wm = resources.displayMetrics
        dlg.window?.setLayout((wm.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        DialogUtils.blurBehind(dlg)
    }

    /** Credits: Name + Profilbild per Discord-ID laden. */
    private fun loadCredits() {
        Thread {
            val u = if (prefs.token.isNotEmpty())
                DiscordApi.getUser(prefs.token, "1303627964734246944")
            else DiscordApi.DiscordUser(false, "", "", "")
            runOnUiThread {
                if (u.ok) {
                    b.txtCredName.text = u.name
                    b.txtCredId.text = "${u.handle} • 1303627964734246944"
                    b.credAvatar.visibility = View.VISIBLE
                    ImageLoader.load(u.avatarUrl, b.credAvatar)
                }
            }
        }.start()
    }

    private fun openUrl(u: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: Exception) { }
    }

    /** Rechte-Pille im Mehr-Tab (x/3 aktiv). */
    private fun updatePermPill() {
        var n = 0
        try {
            if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) n++
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) n++
            if (prefs.autostart) n++
        } catch (e: Exception) { }
        b.morePermPill.text = "$n/3 an"
        b.morePermPill.setTextColor(getColor(if (n == 3) R.color.success else R.color.warning))
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.more_share_text))
        startActivity(Intent.createChooser(send, getString(R.string.more_share)))
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
        PreviewBinder.bind(b.previewCard, act, prefs.safeMode, this, prefs.appId, prefs.token) { refresh() }
        buildPresetRow()
        updatePermPill()
    }

    private fun toggleService() {
        try {
            if (RpcService.isRunning) {
                startService(Intent(this, RpcService::class.java).setAction(RpcService.ACTION_STOP))
            } else {
                if (prefs.token.isEmpty()) {
                    Toast.makeText(this, "Erst Token eintragen! (Tab Einstellungen)", Toast.LENGTH_LONG).show()
                    AnimUtils.launch(this, SettingsActivity::class.java)
                    return
                }
                val i = Intent(this, RpcService::class.java).setAction(RpcService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "Starte … (läuft im Hintergrund weiter)", Toast.LENGTH_SHORT).show()
                ensureActivityImages()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Start blockiert: ${e.message}", Toast.LENGTH_LONG).show()
        }
        handler.postDelayed({ refresh() }, 900)
    }

    /** Fehlende Status-Bilder beim Start automatisch zu Discord hochladen. */
    private fun ensureActivityImages() {
        val act = prefs.loadAct()
        val names = listOf(act.largeImage.trim(), act.smallImage.trim())
            .filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return
        if (prefs.token.isEmpty() || !validAppId(prefs.appId)) return
        // Nur Namen mit lokalem Bild können wir automatisch liefern.
        val local = names.mapNotNull { n ->
            val id = resources.getIdentifier(n, "drawable", packageName)
            if (id != 0) n to id else null
        }
        if (local.isEmpty()) return
        Toast.makeText(this, "⏳ Prüfe Status-Bilder …", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val (ok, assets) = DiscordApi.listAssets(prefs.token, prefs.appId)
                val have = if (ok) assets.map { it.second }.toSet() else emptySet()
                val missing = local.filter { !have.contains(it.first) }
                if (missing.isEmpty()) return@Thread
                var done = 0
                var fail = ""
                for ((n, resId) in missing) {
                    val bmp = BitmapFactory.decodeResource(resources, resId) ?: continue
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val dataUrl = "data:image/png;base64," +
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    val (ok2, res2) = DiscordApi.uploadAsset(prefs.token, prefs.appId, n, dataUrl)
                    if (ok2) done++ else fail = res2.take(120)
                }
                runOnUiThread {
                    if (done > 0) Toast.makeText(this,
                        "✅ $done Bild(er) hochgeladen! (ca. 5 Min warten)", Toast.LENGTH_LONG).show()
                    else if (fail.isNotEmpty()) Toast.makeText(this,
                        "⚠️ Bild-Upload: $fail", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { /* still — Status läuft auch ohne Bild */ }
        }.start()
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
            val item = ItemPresetRowBinding.inflate(LayoutInflater.from(this), b.presetRow, false)
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

    private fun showLetter(item: ItemPresetRowBinding, title: String) {
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
        maybeUploadPresetImage(p)
    }

    /** Preset-Bild aus der App zu Discord hochladen, falls es dort fehlt. */
    private fun maybeUploadPresetImage(p: Preset) {
        val asset = p.act.largeImage.trim()
        if (asset.isEmpty() || p.icon.isEmpty()) return
        val resId = resources.getIdentifier(p.icon, "drawable", packageName)
        if (resId == 0) return
        if (prefs.token.isEmpty() || !validAppId(prefs.appId)) {
            Toast.makeText(this, "💡 Für Preset-Bilder: Token + App-ID eintragen!", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "⏳ Prüfe Preset-Bild …", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val (ok, assets) = DiscordApi.listAssets(prefs.token, prefs.appId)
                if (ok && assets.any { it.second == asset }) return@Thread // schon da
                val bmp = BitmapFactory.decodeResource(resources, resId) ?: return@Thread
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                val data = out.toByteArray()
                val dataUrl = "data:image/png;base64," + Base64.encodeToString(data, Base64.NO_WRAP)
                val (ok2, res2) = DiscordApi.uploadAsset(prefs.token, prefs.appId, asset, dataUrl)
                runOnUiThread {
                    if (ok2) Toast.makeText(this,
                        "✅ Bild '$asset' hochgeladen! (5 Min warten)", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "⚠️ Bild-Upload: ${res2.take(150)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { /* still — Preset läuft auch ohne Bild */ }
        }.start()
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
        val d = DialogImportBinding.inflate(layoutInflater)
        val dlg = Dialog(this, R.style.Theme_Fufcord_Dialog)
        dlg.setContentView(d.root)
        d.etJson.typeface = Typeface.MONOSPACE
        d.btnPaste.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (txt.isNotEmpty()) {
                d.etJson.setText(txt)
                d.txtError.visibility = View.GONE
            } else {
                Toast.makeText(this, "Zwischenablage ist leer", Toast.LENGTH_SHORT).show()
            }
        }
        d.btnClear.setOnClickListener {
            d.etJson.text?.clear()
            d.txtError.visibility = View.GONE
        }
        d.btnCancel.setOnClickListener { dlg.dismiss() }
        d.btnGo.setOnClickListener {
            val raw = d.etJson.text.toString()
            val block = extractJson(raw)
            if (block == null) {
                d.txtError.text = "❌ Kein JSON gefunden — Code einfügen!"
                d.txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            try {
                JSONObject(block)
            } catch (e: Exception) {
                d.txtError.text = "❌ JSON fehlerhaft: ${e.message}"
                d.txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dlg.dismiss()
            doImport(raw)
        }
        dlg.show()
        // 92 % Breite, 86 % Höhe: Text scrollt, Buttons bleiben immer sichtbar
        val wm = resources.displayMetrics
        dlg.window?.setLayout((wm.widthPixels * 0.92).toInt(), (wm.heightPixels * 0.86).toInt())
        DialogUtils.blurBehind(dlg)
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
