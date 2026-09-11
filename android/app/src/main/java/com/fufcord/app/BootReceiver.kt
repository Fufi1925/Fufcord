/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Startet den RPC neu: nach Handy-Neustart & nach App-Update (wenn Autostart an). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            val p = PrefsManager(ctx)
            if (!p.setupDone || !p.autostart || p.token.isEmpty()) return
            val i = Intent(ctx, RpcService::class.java).setAction(RpcService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        } catch (e: Exception) { /* System verbietet es manchmal — egal */ }
    }
}
