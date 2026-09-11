/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySettingsBinding

/** Einstellungen (neu): Zugang, Verhalten, System, Gefahrenzone, App. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private lateinit var statusKeys: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.etToken.setText(prefs.token)
        b.etAppId.setText(prefs.appId)
        b.swSafe.isChecked = prefs.safeMode
        b.swAuto.isChecked = prefs.autostart
        statusKeys = ActConfig.STATUS.keys.toList()
        val statusAdapter = ArrayAdapter(this, R.layout.spinner_item,
            ActConfig.STATUS.values.toList())
        statusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        b.spStatus.adapter = statusAdapter
        b.spStatus.setSelection(statusKeys.indexOf(prefs.loadAct().status).coerceAtLeast(0))
        try {
            @Suppress("DEPRECATION")
            val v = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            b.txtVersion.text = "v$v"
        } catch (_: Exception) { }

        b.toolbarBack.setOnClickListener { AnimUtils.finish(this) }
        AnimUtils.stagger(b.setInner, 80)

        b.btnTestToken.setOnClickListener {
            val t = currentToken()
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

        b.rowBgPerms.setOnClickListener { AnimUtils.launch(this, PermissionActivity::class.java) }
        b.rowSetupAgain.setOnClickListener {
            prefs.setupDone = false
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }
        b.rowSetUpdate.setOnClickListener {
            prefs.skipVersion = ""
            UpdateChecker.check(this)
            Toast.makeText(this, R.string.help_updating, Toast.LENGTH_SHORT).show()
        }
        b.btnWipeToken.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_wipe_token))
                .setMessage(getString(R.string.set_wipe_token_msg))
                .setPositiveButton(getString(R.string.dlg_delete)) { _, _ -> wipeToken() }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }
        b.btnWipeAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_wipe_all))
                .setMessage(getString(R.string.set_wipe_all_msg))
                .setPositiveButton(getString(R.string.dlg_delete)) { _, _ -> wipeAll() }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }

        b.btnSave.setOnClickListener {
            val t = currentToken()
            if (t.isNotEmpty()) prefs.token = t
            prefs.appId = b.etAppId.text.toString().trim()
            prefs.safeMode = b.swSafe.isChecked
            prefs.autostart = b.swAuto.isChecked
            val a = prefs.loadAct()
            a.status = statusKeys.getOrElse(b.spStatus.selectedItemPosition) { "online" }
            prefs.saveAct(a)
            if (RpcService.isRunning) RpcService.refresh(this)
            Toast.makeText(this, R.string.set_saved, Toast.LENGTH_SHORT).show()
            AnimUtils.finish(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePill()
    }

    private fun currentToken(): String =
        b.etToken.text.toString().trim().replace(" ", "")

    /** Rechte-Pille (x/3 aktiv). */
    private fun updatePill() {
        var n = 0
        try {
            if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) n++
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) n++
            if (prefs.autostart) n++
        } catch (e: Exception) { }
        b.setPermPill.text = "$n/3 an"
        b.setPermPill.setTextColor(getColor(if (n == 3) R.color.success else R.color.warning))
    }

    private fun stopRpc() {
        try {
            startService(Intent(this, RpcService::class.java).setAction(RpcService.ACTION_STOP))
        } catch (e: Exception) { }
    }

    private fun wipeToken() {
        prefs.token = ""
        b.etToken.setText("")
        b.txtTokenResult.text = ""
        stopRpc()
        Toast.makeText(this, R.string.set_token_removed, Toast.LENGTH_SHORT).show()
    }

    private fun wipeAll() {
        stopRpc()
        prefs.clearAll()
        Toast.makeText(this, R.string.set_wiped, Toast.LENGTH_SHORT).show()
        val i = Intent(this, SplashActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
        finish()
    }

    @Deprecated("Alte Back-Animation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
