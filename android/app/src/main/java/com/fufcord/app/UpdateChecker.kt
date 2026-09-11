/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Prüft GitHub-Releases auf neue Versionen → Pop-up mit Download. */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/Fufi1925/Fufcord/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun check(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        val prefs = PrefsManager(ctx)
        fun done(found: Boolean) {
            if (onDone == null) return
            (ctx as? AppCompatActivity)?.runOnUiThread { onDone(found) }
        }
        Thread {
            try {
                @Suppress("DEPRECATION")
                val current = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                } catch (e: Exception) {
                    ""
                }
                val req = Request.Builder().url(API)
                    .header("User-Agent", "Fufcord-App")
                    .header("Accept", "application/vnd.github+json").build()
                client.newCall(req).execute().use { r ->
                    if (r.code != 200) { done(false); return@use }
                    val o = JSONObject(r.body?.string() ?: return@use)
                    val tag = o.optString("tag_name", "").trim().removePrefix("v")
                    if (tag.isEmpty() || current.isEmpty()) { done(false); return@use }
                    if (!isNewer(tag, current)) { done(false); return@use }
                    if (prefs.skipVersion == tag) { done(false); return@use }
                    var apkUrl = ""
                    val assets = o.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val u = assets.optJSONObject(i)
                                ?.optString("browser_download_url", "") ?: ""
                            if (u.endsWith(".apk")) {
                                apkUrl = u
                                break
                            }
                        }
                    }
                    val url = apkUrl.ifEmpty { o.optString("html_url", "") }
                    if (url.isEmpty()) { done(false); return@use }
                    val notes = o.optString("body", "").take(800)
                    val act = ctx as? AppCompatActivity ?: run { done(false); return@use }
                    act.runOnUiThread { showDialog(act, prefs, tag, notes, url); onDone?.invoke(true) }
                }
            } catch (e: Exception) { done(false) /* still schweigen — kein Internet? */ }
        }.start()
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(s: String) = s.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun showDialog(act: AppCompatActivity, prefs: PrefsManager,
                           tag: String, notes: String, url: String) {
        val msg = StringBuilder("🎉 Neue Version v$tag ist da!")
        if (notes.isNotEmpty()) msg.append("\n\n$notes")
        msg.append("\n\nJetzt herunterladen & installieren?")
        AlertDialog.Builder(act)
            .setTitle("⬆️ Update verfügbar")
            .setMessage(msg.toString())
            .setCancelable(true)
            .setPositiveButton("⬇️ Herunterladen") { _, _ ->
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) { }
            }
            .setNeutralButton("Überspringen") { _, _ -> prefs.skipVersion = tag }
            .setNegativeButton("Später", null)
            .show()
    }
}
