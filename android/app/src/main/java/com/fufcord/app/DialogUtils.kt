/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.app.Dialog
import android.os.Build
import android.view.WindowManager

/** Dialog-Deko: Weichzeichner hinter Pop-ups (ab Android 12 echtes Blur). */
object DialogUtils {

    fun blurBehind(dlg: Dialog, radius: Int = 48) {
        try {
            dlg.window?.let { w ->
                if (Build.VERSION.SDK_INT >= 31) {
                    val a = w.attributes
                    a.blurBehindRadius = radius
                    w.attributes = a
                }
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } catch (e: Exception) { }
    }
}
