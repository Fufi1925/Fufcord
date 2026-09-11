/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Direkte Discord-API calls (blockierend — immer in Threads aufrufen!). */
object DiscordApi {

    private const val BASE = "https://discord.com/api/v9"
    private const val UA = ("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(path: String, token: String): Pair<Int, String> {
        val req = Request.Builder().url(BASE + path)
            .header("Authorization", token).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            return r.code to (r.body?.string() ?: "")
        }
    }

    /** Token gültig? → (ok, username_oder_fehler) */
    fun getMe(token: String): Pair<Boolean, String> {
        return try {
            val (code, body) = get("/users/@me", token)
            if (code == 200) true to JSONObject(body).optString("username", "?")
            else false to "HTTP $code"
        } catch (e: Exception) {
            false to (e.message ?: "Netzwerkfehler")
        }
    }

    /** Fremden User per ID laden (für Credits). */
    data class DiscordUser(val ok: Boolean, val name: String, val handle: String, val avatarUrl: String)

    fun getUser(token: String, userId: String): DiscordUser {
        return try {
            val (code, body) = get("/users/$userId", token)
            if (code != 200) return DiscordUser(false, "", "", "")
            val o = JSONObject(body)
            val username = o.optString("username", "?")
            val global = o.optString("global_name", "")
                .takeIf { it.isNotEmpty() && it != "null" } ?: username
            val av = o.optString("avatar", "")
            val url = if (av.isNotEmpty() && av != "null")
                "https://cdn.discordapp.com/avatars/$userId/$av.png?size=128"
            else {
                val idx = (userId.toLongOrNull()?.shr(22) ?: 0) % 6
                "https://cdn.discordapp.com/embed/avatars/$idx.png"
            }
            DiscordUser(true, global, "@$username", url)
        } catch (e: Exception) {
            DiscordUser(false, "", "", "")
        }
    }

    /** Eigene Apps → (ok, [(id, name)]) */
    fun listApps(token: String): Pair<Boolean, List<Pair<String, String>>> {
        return try {
            val (code, body) = get("/applications?with_team_applications=true", token)
            if (code != 200) return false to emptyList()
            val out = mutableListOf<Pair<String, String>>()
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o.optString("id") to o.optString("name", "?"))
            }
            true to out
        } catch (e: Exception) {
            false to emptyList()
        }
    }

    /** Neue Discord-App anlegen (wie im Portal) → (ok, appId_oder_fehler). */
    fun createApp(token: String, name: String): Pair<Boolean, String> {
        val clean = name.trim().take(100)
        if (clean.length < 2) return false to "Name zu kurz"
        return try {
            val js = JSONObject().put("name", clean)
            val body = js.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$BASE/applications")
                .header("Authorization", token).header("User-Agent", UA)
                .post(body).build()
            client.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (r.code in 200..299) {
                    val id = JSONObject(txt).optString("id", "")
                    if (id.isNotEmpty()) true to id else false to "Keine ID zurück"
                } else {
                    false to "HTTP ${r.code}: ${txt.take(300)}"
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Netzwerkfehler")
        }
    }

    /** Assets einer App → (ok, [(assetId, name)]) */
    fun listAssets(token: String, appId: String): Pair<Boolean, List<Pair<String, String>>> {
        return try {
            val (code, body) = get("/oauth2/applications/$appId/assets?nocache=true", token)
            if (code != 200) return false to emptyList()
            val out = mutableListOf<Pair<String, String>>()
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o.optString("id") to o.optString("name", "?"))
            }
            true to out
        } catch (e: Exception) {
            false to emptyList()
        }
    }

    /** Bild hochladen → (ok, assetId_oder_fehler). imageDataUrl = data:image/png;base64,...
     * Endpunkt + Format exakt wie das Discord-Developer-Portal:
     * POST /oauth2/applications/{id}/assets mit {name, image, type:"1"}.
     * (Der Pfad ohne /oauth2 ist das neue V2-Schema und lehnt das ab → 400.) */
    fun uploadAsset(token: String, appId: String, name: String, imageDataUrl: String): Pair<Boolean, String> {
        val cleanName = name.trim().lowercase()
        val cleanImg = imageDataUrl.replace("\\s".toRegex(), "")
        if (!cleanName.matches(Regex("[a-z0-9_]{1,32}")))
            return false to "Ungültiger Asset-Name: '$cleanName'"
        return try {
            // Gleichnamiges Asset erst löschen (Update = ersetzen, sonst 400).
            try {
                val (okL, assets) = listAssets(token, appId)
                if (okL) assets.firstOrNull { it.second == cleanName }
                    ?.let { deleteAsset(token, appId, it.first) }
            } catch (e: Exception) { /* egal — Upload versuchen */ }
            var last: Pair<Boolean, String> = false to "Unbekannter Fehler"
            for (asString in listOf(true, false)) {
                val js = JSONObject().put("name", cleanName).put("image", cleanImg)
                if (asString) js.put("type", "1") else js.put("type", 1)
                val body = js.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$BASE/oauth2/applications/$appId/assets")
                    .header("Authorization", token).header("User-Agent", UA)
                    .post(body).build()
                client.newCall(req).execute().use { r ->
                    val txt = r.body?.string() ?: ""
                    if (r.code in 200..299) return true to JSONObject(txt).optString("id", "?")
                    last = false to "HTTP ${r.code}: ${txt.take(500)}"
                    if (r.code != 400) return last
                }
            }
            last
        } catch (e: Exception) {
            false to (e.message ?: "Netzwerkfehler")
        }
    }

    /** Asset löschen. */
    fun deleteAsset(token: String, appId: String, assetId: String): Boolean {
        return try {
            val req = Request.Builder().url("$BASE/oauth2/applications/$appId/assets/$assetId")
                .header("Authorization", token).header("User-Agent", UA)
                .delete().build()
            client.newCall(req).execute().use { it.code in 200..299 }
        } catch (e: Exception) {
            false
        }
    }
}
