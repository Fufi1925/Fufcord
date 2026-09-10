package com.fufcord.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Startet den RPC nach Handy-Neustart neu (wenn Autostart an ist). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (!PrefsManager(ctx).autostart) return
            if (PrefsManager(ctx).token.isEmpty()) return
            val i = Intent(ctx, RpcService::class.java).setAction(RpcService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        } catch (e: Exception) { /* System verbietet es manchmal — egal */ }
    }
}
