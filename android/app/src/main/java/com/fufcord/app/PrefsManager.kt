/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.io.File

/** Speichert alles. Token liegt VERSCHLÜSSELT (Android Keystore). */
class PrefsManager(ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val prefs: SharedPreferences =
        appCtx.getSharedPreferences("fufcord", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private val secure: SharedPreferences = try {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "fufcord_secret", alias, appCtx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        prefs // Fallback (immer noch privat, nur unverschlüsselt)
    }

    var token: String
        get() = secure.getString("token", "") ?: ""
        set(v) = secure.edit().putString("token", v.trim()).apply()

    var appId: String
        get() = prefs.getString("app_id", "") ?: ""
        set(v) = prefs.edit().putString("app_id", v.trim()).apply()

    var safeMode: Boolean
        get() = prefs.getBoolean("safe_mode", false)
        set(v) = prefs.edit().putBoolean("safe_mode", v).apply()

    var autostart: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(v) = prefs.edit().putBoolean("autostart", v).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) = prefs.edit().putBoolean("setup_done", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()

    var skipVersion: String
        get() = prefs.getString("skip_version", "") ?: ""
        set(v) = prefs.edit().putString("skip_version", v).apply()

    var discordPromoSeen: Boolean
        get() = prefs.getBoolean("discord_promo_seen", false)
        set(v) = prefs.edit().putBoolean("discord_promo_seen", v).apply()

    var permissionsDone: Boolean
        get() = prefs.getBoolean("permissions_done", false)
        set(v) = prefs.edit().putBoolean("permissions_done", v).apply()

    var tokenAsked: Boolean
        get() = prefs.getBoolean("token_asked", false)
        set(v) = prefs.edit().putBoolean("token_asked", v).apply()

    /** Alles löschen (Gefahrenzone): Einstellungen, Token, Presets. */
    fun clearAll() {
        try { prefs.edit().clear().apply() } catch (e: Exception) { }
        try { secure.edit().clear().apply() } catch (e: Exception) { }
        try { presetDir().deleteRecursively() } catch (e: Exception) { }
    }

    fun loadAct(): ActConfig {
        val raw = prefs.getString("activity", "") ?: ""
        if (raw.isEmpty()) return ActConfig(
            details = "Custom Rich Presence wie Vencord",
            state = "läuft auf Android 📱")
        return try {
            ActConfig.sanitize(JSONObject(raw)).first
        } catch (e: Exception) {
            ActConfig()
        }
    }

    fun saveAct(a: ActConfig) {
        prefs.edit().putString("activity", a.toJson().toString()).apply()
    }

    // ---- Eigene Presets (interne Dateien) ----
    private fun presetDir(): File {
        val d = File(appCtx.filesDir, "presets")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun saveCustomPreset(name: String, status: String, appId: String, act: ActConfig) {
        val o = JSONObject().put("status", status).put("application_id", appId)
            .put("activity", act.toJson())
        File(presetDir(), "$name.json").writeText(o.toString())
    }

    fun loadCustomPresets(): List<Preset> {
        val out = mutableListOf<Preset>()
        for (f in presetDir().listFiles() ?: return out) {
            if (!f.name.endsWith(".json")) continue
            try {
                val o = JSONObject(f.readText())
                val act = ActConfig.sanitize(o.optJSONObject("activity")).first
                act.status = o.optString("status", "online").lowercase()
                    .takeIf { ActConfig.STATUS.containsKey(it) } ?: "online"
                out.add(Preset(f.nameWithoutExtension, f.nameWithoutExtension,
                    o.optString("status", "online"), o.optString("application_id", ""),
                    act, ""))
            } catch (e: Exception) { /* kaputtes Preset ignorieren */ }
        }
        return out.sortedBy { it.title }
    }

    fun deleteCustomPreset(name: String) {
        File(presetDir(), "$name.json").delete()
    }

    // ---- Mitgelieferte Presets aus assets/ ----
    fun loadBundledPresets(): List<Preset> {
        val out = mutableListOf<Preset>()
        try {
            val txt = appCtx.assets.open("bundled_presets.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONObject(txt).optJSONArray("presets") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val act = ActConfig.sanitize(o.optJSONObject("activity")).first
                act.status = o.optString("status", "online").lowercase()
                    .takeIf { ActConfig.STATUS.containsKey(it) } ?: "online"
                out.add(Preset(o.optString("file"), o.optString("title"),
                    o.optString("status", "online"), "", act, o.optString("icon", "")))
            }
        } catch (e: Exception) { /* keine Bundles */ }
        return out
    }
}
