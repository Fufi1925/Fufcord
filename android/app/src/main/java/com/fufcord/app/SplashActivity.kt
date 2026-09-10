/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivitySplashBinding

/** Splash mit Lade-Animation — leitet danach zu Onboarding, Setup oder Main weiter. */
class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        try {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            b.txtSplashVersion.text = "v${p.versionName}"
        } catch (_: Exception) { }

        // Gestaffeltes Einblenden: Logo → Titel → Untertitel → Ladebalken
        b.splashLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        val t1 = AnimationUtils.loadAnimation(this, R.anim.fade_in); t1.startOffset = 100
        val t2 = AnimationUtils.loadAnimation(this, R.anim.fade_in); t2.startOffset = 200
        val t3 = AnimationUtils.loadAnimation(this, R.anim.fade_in); t3.startOffset = 300
        b.splashTitle.startAnimation(t1)
        b.splashTag.startAnimation(t2)
        b.splashBar.startAnimation(t3)

        handler.postDelayed({ route() }, 1200)
    }

    private fun route() {
        if (isFinishing) return
        val prefs = PrefsManager(this)
        val next = when {
            !prefs.onboardingDone -> OnboardingActivity::class.java
            !prefs.setupDone -> SetupActivity::class.java
            else -> MainActivity::class.java
        }
        startActivity(Intent(this, next))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
