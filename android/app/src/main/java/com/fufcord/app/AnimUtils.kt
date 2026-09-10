/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap

/** Zentrale Animationen: Übergänge, Entrance-Stagger, Pulsieren, Einblenden. */
object AnimUtils {

    private val pulses = WeakHashMap<View, AnimatorSet>()

    /** Activity starten mit Slide-Übergang. */
    fun launch(caller: Activity, target: Class<*>) {
        caller.startActivity(Intent(caller, target))
        caller.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /** Activity schließen mit Fade-Übergang. */
    fun finish(caller: Activity) {
        caller.finish()
        caller.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    /** Kinder erscheinen gestaffelt von unten (Entrance). */
    fun stagger(container: ViewGroup, delayStep: Long = 70L) {
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            v.alpha = 0f
            v.translationY = 48f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * delayStep)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Einzelne View von rechts einblenden (z.B. Setup-Schritte). */
    fun fadeSlideIn(v: View) {
        v.alpha = 0f
        v.translationX = 64f
        v.visibility = View.VISIBLE
        v.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Endlos-Pulsieren für Status-Dot. */
    fun startPulse(dot: View) {
        stopPulse(dot)
        val sx = ObjectAnimator.ofFloat(dot, View.SCALE_X, 1f, 1.5f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        val sy = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 1f, 1.5f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        val a = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.55f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        AnimatorSet().apply {
            playTogether(sx, sy, a)
            interpolator = AccelerateDecelerateInterpolator()
            start()
            pulses[dot] = this
        }
    }

    fun stopPulse(dot: View) {
        pulses.remove(dot)?.cancel()
        dot.scaleX = 1f
        dot.scaleY = 1f
        dot.alpha = 1f
    }

    /** Rotations-Loader für Icons (z.B. Refresh). */
    fun startSpin(v: View) {
        v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.spin))
    }

    fun stopSpin(v: View) {
        v.clearAnimation()
    }
}
