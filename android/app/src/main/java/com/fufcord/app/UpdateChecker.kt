/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.DialogUpdateBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Prüft GitHub-Releases auf neue Versionen → Custom-Pop-up mit Download. */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/Fufi1925/Fufcord/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun check(ctx: Context) {
        val prefs = PrefsManager(ctx)
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
                    if (r.code != 200) return@use
                    val o = JSONObject(r.body?.string() ?: return@use)
                    val tag = o.optString("tag_name", "").trim().removePrefix("v")
                    if (tag.isEmpty() || current.isEmpty()) return@use
                    if (!isNewer(tag, current)) return@use
                    // Versions-Neustart: alte 4.x-Tags enthielten 1.x-Apps (4.2→1.03, 4.3→1.04). Neue Tags = App-Version.
                    // Ohne Guard würden 1.x-Geräte ewig zum "Update" auf die eigene Version raten.
                    if (tag.startsWith("4.") && current.startsWith("1.")) return@use
                    if (prefs.skipVersion == tag) return@use
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
                    if (url.isEmpty()) return@use
                    val notes = o.optString("body", "").take(1200)
                    val act = ctx as? AppCompatActivity ?: return@use
                    act.runOnUiThread { showDialog(act, prefs, tag, notes, url) }
                }
            } catch (e: Exception) { /* still schweigen — kein Internet? */ }
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
        val d = DialogUpdateBinding.inflate(act.layoutInflater)
        val dlg = Dialog(act, R.style.Theme_Fufcord_Dialog)
        dlg.setContentView(d.root)
        d.txtUpdVersion.text = "v$tag"
        d.txtUpdNotes.text = notes.ifEmpty { act.getString(R.string.upd_notes_empty) }
        d.btnUpdGo.setOnClickListener {
            try {
                act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) { }
            dlg.dismiss()
        }
        d.btnUpdLater.setOnClickListener { dlg.dismiss() }
        d.btnUpdSkip.setOnClickListener { prefs.skipVersion = tag; dlg.dismiss() }
        dlg.show()
        val wm = act.resources.displayMetrics
        dlg.window?.setLayout((wm.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        DialogUtils.blurBehind(dlg)
    }
}
