/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.view.View
import com.fufcord.app.databinding.ViewPreviewBinding

/** 1:1-Vorschau der Discord-Aktivitätskarte (dunkles Discord-Design). */
object PreviewBinder {

    fun bind(p: ViewPreviewBinding, act: ActConfig, safe: Boolean) {
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

        p.previewTime.visibility = if (act.useTimestamp) View.VISIBLE else View.GONE

        val showRich = !safe
        // Großes Bild (Platzhalter mit Asset-Namen — Discord lädt das echte Bild)
        if (showRich && act.largeImage.isNotEmpty()) {
            p.previewLargeWrap.visibility = View.VISIBLE
            p.previewLargeLabel.text = "🖼️ ${act.largeImage}"
            if (act.smallImage.isNotEmpty()) {
                p.previewSmallWrap.visibility = View.VISIBLE
                p.previewSmallLabel.text = act.smallImage
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
}
