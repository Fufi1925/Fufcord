/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.fufcord.app.databinding.ViewPreviewBinding

/** 1:1-Vorschau der Discord-Aktivitätskarte (dunkles Discord-Design). */
object PreviewBinder {

    private val urlCache = mutableMapOf<String, String>()
    private val resolving = mutableSetOf<String>()
    private val main = Handler(Looper.getMainLooper())

    fun bind(p: ViewPreviewBinding, act: ActConfig, safe: Boolean,
             ctx: Context? = null, appId: String = "", token: String = "",
             preferServer: Set<String> = emptySet(),
             rebind: (() -> Unit)? = null) {
        p.previewType.text = when (act.type) {
            0 -> "SPIELT GERADE"
            1 -> "STREAMT GERADE"
            2 -> "HÖRT GERADE"
            3 -> "SCHAUT GERADE"
            5 -> "NIMMT TEIL"
            else -> "AKTIVITÄT"
        }
        p.previewName.text = act.name.ifEmpty { "Fufcord" }

        p.previewDetails.visibility = if (act.details.isNotEmpty()) View.VISIBLE else View.GONE
        p.previewDetails.text = act.details
        p.previewState.visibility = if (act.state.isNotEmpty()) View.VISIBLE else View.GONE
        p.previewState.text = act.state

        if (act.useTimestamp) {
            p.previewTime.visibility = View.VISIBLE
            p.previewTime.text = if (act.timestampMode == 1 && act.timestampOffsetSec > 0)
                "⏱ seit ${act.offsetLabel()} (+ live)" else "⏱ läuft seit Start"
        } else {
            p.previewTime.visibility = View.GONE
        }

        val showRich = !safe
        // Großes Bild: echtes Bild laden (lokal oder aus dem Discord-CDN).
        if (showRich && act.largeImage.isNotEmpty()) {
            p.previewLargeWrap.visibility = View.VISIBLE
            bindImage(ctx, appId, token, act.largeImage.trim(),
                p.previewLarge, p.previewLargeLabel, true, preferServer, rebind)
            if (act.smallImage.isNotEmpty()) {
                p.previewSmallWrap.visibility = View.VISIBLE
                bindImage(ctx, appId, token, act.smallImage.trim(),
                    p.previewSmall, p.previewSmallLabel, false, preferServer, rebind)
            } else {
                p.previewSmallWrap.visibility = View.GONE
            }
        } else {
            p.previewLargeWrap.visibility = View.GONE
        }
        // Buttons
        val btns = if (showRich) act.buttons.take(2) else emptyList()
        if (btns.isNotEmpty()) {
            p.previewBtnRow.visibility = View.VISIBLE
            p.previewBtn1.visibility = View.VISIBLE
            p.previewBtn1.text = btns[0].label
            if (btns.size > 1) {
                p.previewBtn2.visibility = View.VISIBLE
                p.previewBtn2.text = btns[1].label
            } else {
                p.previewBtn2.visibility = View.GONE
            }
        } else {
            p.previewBtnRow.visibility = View.GONE
        }
        p.previewSafe.visibility = if (safe) View.VISIBLE else View.GONE
    }

    /** Bild in die Vorschau laden: 1) lokal (alle Preset-Bilder), 2) Discord-CDN. */
    private fun bindImage(ctx: Context?, appId: String, token: String, name: String,
                          img: ImageView, label: TextView, big: Boolean,
                          preferServer: Set<String>,
                          rebind: (() -> Unit)?) {
        if (name.isEmpty()) return
        // 1) Lokales Bild aus der App → sofort da, geht auch offline.
        // Ausgenommen: Namen mit eigenem Server-Bild (Custom-Uploads).
        if (ctx != null && !preferServer.contains(name)) {
            val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (resId != 0) {
                img.setImageResource(resId)
                label.visibility = View.GONE
                return
            }
        }
        // 2) Bereits bekannte CDN-Adresse aus dem Zwischenspeicher.
        val key = "$appId/$name"
        urlCache[key]?.let {
            ImageLoader.load(it, img)
            label.visibility = View.GONE
            return
        }
        // 3) Platzhalter + Name→Bild einmalig im Hintergrund auflösen.
        label.visibility = View.VISIBLE
        label.text = if (big) "🖼️ $name" else name
        img.setImageDrawable(null)
        if (appId.isNotEmpty() && token.isNotEmpty() && resolving.add(key)) {
            Thread {
                try {
                    val (ok, assets) = DiscordApi.listAssets(token, appId)
                    if (ok) assets.firstOrNull { it.second == name }?.let {
                        urlCache[key] = ImageLoader.appAssetUrl(appId, it.first)
                        main.post { rebind?.invoke() }
                    }
                } catch (e: Exception) { }
                finally { resolving.remove(key) }
            }.start()
        }
    }
}
