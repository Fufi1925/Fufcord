/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import org.json.JSONArray
import org.json.JSONObject

// ===================================================================== Models
// Entspricht 1:1 dem Termux-main.py (sanitize, build, valid) — gleiche Logik!

data class ActButton(val label: String, val url: String)

data class ActConfig(
    var name: String = "Fufcord",
    var type: Int = 0,
    var details: String = "",
    var state: String = "",
    var largeImage: String = "",
    var largeText: String = "",
    var smallImage: String = "",
    var smallText: String = "",
    var streamUrl: String = "",
    var buttons: MutableList<ActButton> = mutableListOf(),
    var useTimestamp: Boolean = true,
    var partyCurrent: Int = 0,
    var partyMax: Int = 0,
    var status: String = "online"
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name); o.put("type", type)
        o.put("details", details); o.put("state", state)
        o.put("large_image", largeImage); o.put("large_text", largeText)
        o.put("small_image", smallImage); o.put("small_text", smallText)
        o.put("stream_url", streamUrl)
        val arr = JSONArray()
        for (b in buttons) arr.put(JSONObject().put("label", b.label).put("url", b.url))
        o.put("buttons", arr)
        o.put("use_timestamp", useTimestamp)
        o.put("party_current", partyCurrent); o.put("party_max", partyMax)
        o.put("status", status)
        return o
    }

    /** Baut das Discord-Activity-Objekt. safe=true → nur Text (geht immer). */
    fun toPresenceActivity(appId: String, safe: Boolean, startTs: Long): JSONObject {
        val a = JSONObject()
        a.put("name", name.ifEmpty { "Fufcord" })
        a.put("type", type)
        a.put("created_at", System.currentTimeMillis())
        if (details.isNotEmpty()) a.put("details", details.take(128))
        if (state.isNotEmpty()) a.put("state", state.take(128))
        if (safe) {
            if (useTimestamp) a.put("timestamps", JSONObject().put("start", startTs))
            return a
        }
        if (validAppId(appId)) a.put("application_id", appId)
        val hasApp = validAppId(appId)
        if (hasApp) {
            val assets = JSONObject()
            if (largeImage.isNotEmpty()) assets.put("large_image", largeImage.take(256))
            if (largeText.isNotEmpty()) assets.put("large_text", largeText.take(128))
            if (smallImage.isNotEmpty()) assets.put("small_image", smallImage.take(256))
            if (smallText.isNotEmpty()) assets.put("small_text", smallText.take(128))
            if (assets.length() > 0) a.put("assets", assets)
        }
        if (type == 1) a.put("url", streamUrl.ifEmpty { "https://twitch.tv/" })
        if (hasApp && buttons.isNotEmpty()) {
            val arr = JSONArray()
            for (b in buttons.take(2)) {
                if (b.label.isNotEmpty() && b.url.isNotEmpty())
                    arr.put(JSONObject().put("label", b.label.take(32)).put("url", b.url))
            }
            if (arr.length() > 0) a.put("buttons", arr)
        }
        if (useTimestamp) a.put("timestamps", JSONObject().put("start", startTs))
        if (partyCurrent > 0 && partyMax > 0)
            a.put("party", JSONObject().put("id", "fufcord-party")
                .put("size", JSONArray().put(partyCurrent).put(partyMax)))
        return a
    }

    companion object {
        val TYPES = mapOf(0 to "Spielt", 1 to "Streamt", 2 to "Hört",
            3 to "Schaut zu", 4 to "Benutzerdefiniert", 5 to "Tritt an in")
        val STATUS = mapOf("online" to "🟢 Online", "idle" to "🌙 Abwesend",
            "dnd" to "⛔ Bitte nicht stören", "invisible" to "⚫ Unsichtbar")

        private val EMOJI = Regex("[\\p{So}\\p{Sk}\u200D\uFE0F]")

        /** Bereinigt rohe JSON-Daten. Gibt (config, warnungen) zurück. */
        fun sanitize(raw: JSONObject?): Pair<ActConfig, List<String>> {
            val warns = mutableListOf<String>()
            if (raw == null) return ActConfig() to listOf("Ungültiges Format — Standard genommen.")
            val c = ActConfig()
            val t = raw.optInt("type", 0)
            c.type = if (TYPES.containsKey(t)) t else { warns.add("Typ ungültig → 'Spielt'."); 0 }
            c.name = raw.optString("name", "Fufcord").take(256)
            c.details = raw.optString("details", "").take(256)
            c.state = raw.optString("state", "").take(256)
            if (raw.optString("details", "").length > 128 || raw.optString("state", "").length > 128)
                warns.add("Details/State zu lang → gekürzt.")
            c.largeImage = raw.optString("large_image", "").take(256)
            c.largeText = raw.optString("large_text", "").take(256)
            c.smallImage = raw.optString("small_image", "").take(256)
            c.smallText = raw.optString("small_text", "").take(256)
            c.streamUrl = raw.optString("stream_url", "").take(256)
            val arr = raw.optJSONArray("buttons")
            if (arr != null) {
                for (i in 0 until minOf(arr.length(), 2)) {
                    val b = arr.optJSONObject(i) ?: continue
                    var label = b.optString("label", "").trim()
                    val url = b.optString("url", "").trim()
                    if (EMOJI.containsMatchIn(label)) {
                        label = EMOJI.replace(label, "").trim()
                        warns.add("Button ${i + 1}: Emojis entfernt (blockieren die Anzeige!).")
                    }
                    if (label.isEmpty()) { warns.add("Button ${i + 1}: leer → entfernt."); continue }
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                        warns.add("Button ${i + 1}: Link ungültig → entfernt."); continue
                    }
                    c.buttons.add(ActButton(label.take(32), url))
                }
                if (arr.length() > 2) warns.add("Mehr als 2 Buttons → nur erste 2 behalten.")
            }
            c.useTimestamp = raw.optBoolean("use_timestamp", true)
            c.partyCurrent = maxOf(0, raw.optInt("party_current", 0))
            c.partyMax = maxOf(0, raw.optInt("party_max", 0))
            val st = raw.optString("status", "online").lowercase()
            c.status = if (STATUS.containsKey(st)) st else "online"
            if (c.name.isBlank()) { c.name = "Fufcord"; warns.add("Name leer → 'Fufcord'.") }
            return c to warns
        }

        fun hasEmoji(s: String): Boolean = EMOJI.containsMatchIn(s)
        fun stripEmoji(s: String): String = EMOJI.replace(s, "").trim()
    }
}

fun validAppId(appId: String): Boolean {
    val a = appId.trim()
    if (a.isEmpty() || a.startsWith("DEINE")) return false
    return a.all { it.isDigit() } && a.length >= 15
}

data class Preset(val file: String, val title: String, val status: String,
                  val appId: String, val act: ActConfig, val icon: String)
