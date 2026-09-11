/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/** Hintergrund-Service: hält die Discord-Verbindung dauerhaft am Leben. */
class RpcService : Service() {

    companion object {
        const val ACTION_START = "com.fufcord.app.START"
        const val ACTION_STOP = "com.fufcord.app.STOP"
        const val ACTION_REFRESH = "com.fufcord.app.REFRESH"
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "fufcord_rpc"

        @Volatile var isRunning = false
        @Volatile var statusText = "Gestoppt"
        @Volatile var activityName = ""
        @Volatile var startedAtMs = 0L

        fun refresh(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, RpcService::class.java).setAction(ACTION_REFRESH))
            } catch (e: Exception) { }
        }
    }

    private var gw: GatewayClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var backoff = 5
    private var reconnectTask: Runnable? = null
    private var watchdogTask: Runnable? = null
    private var startTs = System.currentTimeMillis()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            try {
                gw?.sendPresence(buildPresence())
                updateNotif("✅ Online — aktualisiert • $activityName")
            } catch (e: Exception) { }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = PrefsManager(this)
        val token = prefs.token
        if (token.isEmpty()) {
            statusText = "Kein Token! In App eintragen."
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            val n = buildNotif("Verbinde mit Discord...")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            statusText = "Start blockiert: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        startTs = System.currentTimeMillis()
        startedAtMs = startTs
        connect()
        startWatchdog()
        return START_STICKY
    }

    private fun buildPresence(): JSONObject {
        val prefs = PrefsManager(this)
        val act = prefs.loadAct()
        val safe = prefs.safeMode
        activityName = act.name
        val a = act.toPresenceActivity(prefs.appId, safe, startTs)
        return JSONObject()
            .put("since", System.currentTimeMillis())
            .put("activities", JSONArray().put(a))
            .put("status", act.status)
            .put("afk", false)
    }

    private fun connect() {
        cancelReconnect()
        val prefs = PrefsManager(this)
        statusText = "Verbinde..."
        updateNotif("Verbinde mit Discord...")
        val presence = buildPresence()
        val client = GatewayClient()
        gw = client
        client.connect(prefs.token, presence, object : GatewayClient.Listener {
            override fun onReady(username: String) {
                backoff = 5
                handler.post {
                    client.sendPresence(buildPresence())
                    statusText = "Online als $username"
                    updateNotif("✅ Online als $username • ${activityName}")
                }
            }

            override fun onClosed(code: Int) {
                handler.post {
                    if (code == 4004) {
                        statusText = "❌ Token ungültig!"
                        updateNotif("❌ Token ungültig — neuen eintragen")
                        stopSelf()
                    } else {
                        scheduleReconnect("Verbindung geschlossen ($code)")
                    }
                }
            }

            override fun onFailure(msg: String) {
                handler.post { scheduleReconnect(msg) }
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        cancelReconnect()
        statusText = "Neuversuch in ${backoff}s..."
        updateNotif("✖ $reason — Neuversuch in ${backoff}s")
        reconnectTask = Runnable {
            reconnectTask = null
            connect()
        }
        handler.postDelayed(reconnectTask!!, backoff * 1000L)
        backoff = minOf(backoff * 2, 60)
    }

    private fun startWatchdog() {
        stopWatchdog()
        watchdogTask = object : Runnable {
            override fun run() {
                try {
                    val silent = System.currentTimeMillis() - GatewayClient.lastMessageMs
                    if (isRunning && GatewayClient.lastMessageMs > 0 && silent > 150000) {
                        try { gw?.disconnect() } catch (e: Exception) { }
                        connect()
                    }
                } catch (e: Exception) { }
                watchdogTask?.let { handler.postDelayed(it, 60000) }
            }
        }
        handler.postDelayed(watchdogTask!!, 60000)
    }

    private fun stopWatchdog() {
        watchdogTask?.let { handler.removeCallbacks(it) }
        watchdogTask = null
    }

    private fun cancelReconnect() {
        reconnectTask?.let { handler.removeCallbacks(it) }
        reconnectTask = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Fufcord RPC", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotif(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        val stopIt = PendingIntent.getService(
            this, 1, Intent(this, RpcService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fufcord RPC läuft")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIt)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotif(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotif(text))
        } catch (e: Exception) { /* egal */ }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App weggewischt? RPC laeuft trotzdem weiter -> Service neu starten.
        if (!isRunning) return
        try {
            val i = Intent(this, RpcService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        cancelReconnect()
        stopWatchdog()
        gw?.disconnect()
        gw = null
        isRunning = false
        startedAtMs = 0L
        statusText = "Gestoppt"
        super.onDestroy()
    }
}
