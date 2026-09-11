/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Akku-Optimierung: Ohne Ausnahme killt das Handy den RPC im Hintergrund. */
object PowerHelper {

    fun isExempt(ctx: android.content.Context): Boolean {
        return try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    fun requestExempt(act: AppCompatActivity) {
        try {
            act.startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${act.packageName}")))
        } catch (e: Exception) {
            try {
                act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(act, "Bitte manuell: Einstellungen → Akku → Fufcord → Nicht optimieren",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    fun explainAndRequest(act: AppCompatActivity, onDone: () -> Unit = {}) {
        if (isExempt(act)) {
            onDone()
            return
        }
        AlertDialog.Builder(act)
            .setTitle("🔋 Immer im Hintergrund?")
            .setMessage("Damit Fufcord auch bei geschlossener App weiterläuft, erlaube bitte die Ausnahme von der Akku-Optimierung.\n\nSonst stoppt dein Handy die Verbindung!")
            .setCancelable(false)
            .setPositiveButton("✅ Erlauben") { _, _ -> requestExempt(act); onDone() }
            .setNegativeButton("Später") { _, _ -> onDone() }
            .show()
    }
}
