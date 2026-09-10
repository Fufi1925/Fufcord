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

    /** Assets einer App → (ok, [(assetId, name)]) */
    fun listAssets(token: String, appId: String): Pair<Boolean, List<Pair<String, String>>> {
        return try {
            val (code, body) = get("/applications/$appId/assets", token)
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

    /** Bild hochladen → (ok, assetId_oder_fehler). imageDataUrl = data:image/png;base64,... */
    fun uploadAsset(token: String, appId: String, name: String, imageDataUrl: String): Pair<Boolean, String> {
        return try {
            val body = JSONObject().put("name", name).put("type", 1).put("image", imageDataUrl).toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$BASE/applications/$appId/assets")
                .header("Authorization", token).header("User-Agent", UA)
                .post(body).build()
            client.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (r.code in 200..299) true to JSONObject(txt).optString("id", "?")
                else false to "HTTP ${r.code}: ${txt.take(120)}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Netzwerkfehler")
        }
    }

    /** Asset löschen. */
    fun deleteAsset(token: String, appId: String, assetId: String): Boolean {
        return try {
            val req = Request.Builder().url("$BASE/applications/$appId/assets/$assetId")
                .header("Authorization", token).header("User-Agent", UA)
                .delete().build()
            client.newCall(req).execute().use { it.code in 200..299 }
        } catch (e: Exception) {
            false
        }
    }
}
