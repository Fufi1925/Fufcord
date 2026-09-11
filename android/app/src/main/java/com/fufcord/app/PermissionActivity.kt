/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.fufcord.app.databinding.ActivityPermissionsBinding

/** Rechte-Assistent nach dem Setup: Mitteilungen, Akku, Autostart. */
class PermissionActivity : AppCompatActivity() {

    private lateinit var b: ActivityPermissionsBinding
    private lateinit var prefs: PrefsManager
    private var fresh = true

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updatePills() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)
        fresh = !prefs.permissionsDone

        b.btnPermNotif.setOnClickListener { askNotif() }
        b.btnPermBattery.setOnClickListener { askBattery() }
        b.btnPermAuto.setOnClickListener { openAutostart() }
        b.btnPermAutoDone.setOnClickListener {
            prefs.autostart = true
            updatePills()
            Toast.makeText(this, R.string.perm_auto_ok, Toast.LENGTH_SHORT).show()
        }
        b.btnPermNext.setOnClickListener { done() }
        b.btnPermLater.setOnClickListener { done() }
        AnimUtils.stagger(b.permInner, 90)
    }

    override fun onResume() {
        super.onResume()
        updatePills()
    }

    private fun done() {
        prefs.permissionsDone = true
        if (fresh) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        } else {
            AnimUtils.finish(this)
        }
    }

    @Deprecated("Eigener Back-Flow")
    override fun onBackPressed() {
        done()
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            try {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(i)
            } catch (e: Exception) {
                openAppDetails()
            }
        }
    }

    private fun askBattery() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                updatePills()
                return
            }
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) { }
        }
    }

    /** Hersteller-Autostart-Seiten der Reihe nach versuchen, sonst App-Info. */
    private fun openAutostart() {
        val targets = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.htc.pitroad" to "com.htc.pitroad.landingpage.activity.LandingPageActivity",
            "com.evenwell.powersaving.g3" to "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
        )
        for ((pkg, cls) in targets) {
            try {
                val i = Intent().setComponent(ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (e: Exception) { /* weiter versuchen */ }
        }
        openAppDetails()
    }

    private fun openAppDetails() {
        try {
            startActivity(Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        } catch (e: Exception) { }
    }

    private fun pill(v: TextView, on: Boolean) {
        v.text = getString(if (on) R.string.perm_on else R.string.perm_off)
        v.setTextColor(getColor(if (on) R.color.success else R.color.danger))
    }

    private fun updatePills() {
        try {
            pill(b.pillNotif, NotificationManagerCompat.from(this).areNotificationsEnabled())
        } catch (e: Exception) { }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pill(b.pillBattery, pm.isIgnoringBatteryOptimizations(packageName))
        } catch (e: Exception) { }
        pill(b.pillAuto, prefs.autostart)
    }
}
