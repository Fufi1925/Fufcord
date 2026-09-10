/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Discord Gateway (WebSocket): Identify, Heartbeat, Presence. */
class GatewayClient {

    companion object {
        @Volatile var lastMessageMs: Long = 0
    }

    interface Listener {
        fun onReady(username: String)
        fun onClosed(code: Int)
        fun onFailure(msg: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // kein Timeout — Heartbeat hält am Leben
        .build()

    private var ws: WebSocket? = null
    private var seq: Int? = null
    private var listener: Listener? = null
    private var closed = false
    private val handler = Handler(Looper.getMainLooper())
    private var hbTask: Runnable? = null

    fun connect(token: String, presence: JSONObject, cb: Listener) {
        disconnect()
        closed = false
        seq = null
        listener = cb
        val req = Request.Builder()
            .url("wss://gateway.discord.gg/?v=10&encoding=json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) discord-android")
            .build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMsg(text, token, presence)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopHeartbeat()
                if (!closed) listener?.onClosed(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                stopHeartbeat()
                if (!closed) listener?.onFailure(t.message ?: "Verbindung fehlgeschlagen")
            }
        })
    }

    private fun handleMsg(text: String, token: String, presence: JSONObject) {
        lastMessageMs = System.currentTimeMillis()
        val msg = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        if (!msg.isNull("s")) seq = msg.optInt("s")
        when (msg.optInt("op", -1)) {
            10 -> { // Hello
                val interval = msg.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                startHeartbeat(interval)
                val id = JSONObject()
                    .put("token", token)
                    .put("intents", 0)
                    .put("properties", JSONObject()
                        .put("os", "Android")
                        .put("browser", "Discord Android")
                        .put("device", "Android"))
                    .put("presence", presence)
                ws?.send(JSONObject().put("op", 2).put("d", id).toString())
            }
            0 -> { // Dispatch
                when (msg.optString("t")) {
                    "READY" -> {
                        val name = msg.optJSONObject("d")
                            ?.optJSONObject("user")?.optString("username", "?") ?: "?"
                        listener?.onReady(name) // Service sendet danach OP 3
                    }
                }
            }
            7, 9 -> ws?.close(4000, "reconnect") // Reconnect / Invalid Session
        }
    }

    fun sendPresence(presence: JSONObject) {
        ws?.send(JSONObject().put("op", 3).put("d", presence).toString())
    }

    private fun startHeartbeat(intervalMs: Long) {
        stopHeartbeat()
        hbTask = object : Runnable {
            override fun run() {
                val payload = JSONObject().put("op", 1)
                val s = seq
                if (s == null) payload.put("d", JSONObject.NULL) else payload.put("d", s)
                ws?.send(payload.toString())
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(hbTask!!, intervalMs)
    }

    private fun stopHeartbeat() {
        hbTask?.let { handler.removeCallbacks(it) }
        hbTask = null
    }

    fun disconnect() {
        closed = true
        stopHeartbeat()
        try {
            ws?.close(1000, "bye")
        } catch (e: Exception) { /* egal */ }
        ws = null
        listener = null
    }
}
