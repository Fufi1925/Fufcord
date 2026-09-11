/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityTokenFetchBinding

/**
 * Holt den Discord User-Token automatisch: Der Nutzer meldet sich in der
 * eingebetteten offiziellen Discord-Seite an (Passwort sieht die App nie —
 * das läuft komplett auf discord.com). Danach liest die App nur den
 * Anmelde-Code aus dem Browser-Speicher und speichert ihn sicher lokal.
 */
class TokenFetchActivity : AppCompatActivity() {

    private lateinit var b: ActivityTokenFetchBinding
    private lateinit var prefs: PrefsManager
    private var done = false
    private var lastAutoUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTokenFetchBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        val ws = b.webFetch.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.userAgentString = ("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(b.webFetch, true)
        } catch (e: Exception) { }

        b.webFetch.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                b.fetchProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                b.fetchProgress.visibility = View.GONE
            }

            // Feuert auch bei Navigation innerhalb der App (Login → Übersicht).
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!done && url != lastAutoUrl && looksLoggedIn(url)) {
                    lastAutoUrl = url
                    tryExtract(auto = true)
                }
            }
        }

        b.btnFetchBack.setOnClickListener { finish() }
        b.btnFetchCheck.setOnClickListener { tryExtract(auto = false) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.webFetch.canGoBack()) b.webFetch.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        b.webFetch.loadUrl("https://discord.com/login")
    }

    /** Eingeloggt = Discord zeigt App-Inhalte statt der Login-Seite. */
    private fun looksLoggedIn(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains("/login") || u.contains("/register") || u.contains("/verify")) return false
        return u.contains("/channels/") || u.contains("/app") || u.contains("/developers")
                || u.contains("/store") || u.contains("/shop") || u.contains("/guild-discovery")
    }

    /** Liest den Token aus dem Browser-Speicher der Discord-Seite. */
    private fun tryExtract(auto: Boolean) {
        if (done) return
        b.txtFetchStatus.text = getString(R.string.fetch_working)
        // 1) Direkt-Schlüssel, 2) Notfall: alle Einträge nach Token-Muster absuchen.
        val js = "(function(){try{var t=localStorage.getItem('token');if(t)return t;" +
                "for(var i=0;i<localStorage.length;i++){" +
                "var v=localStorage.getItem(localStorage.key(i))||'';" +
                "if(/[A-Za-z0-9\\-_]{20,}\\.[A-Za-z0-9\\-_]{6}\\.[A-Za-z0-9\\-_]{10,}/.test(v)" +
                "||/^\"?mfa\\.[A-Za-z0-9\\-_]{20,}/.test(v))return v;}return '';}catch(e){return '';}})();"
        b.webFetch.evaluateJavascript(js) { res -> handleResult(res, auto) }
    }

    private fun handleResult(res: String?, auto: Boolean) {
        if (done) return
        var t = res?.trim() ?: ""
        // evaluateJavascript liefert JSON: "\"...\"" oder null.
        if (t == "null") t = ""
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        // Der gespeicherte Wert ist selbst nochmal in Anführungszeichen.
        t = t.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length - 1)
        }
        t = t.replace(" ", "")
        if (t.length < 20) {
            b.txtFetchStatus.text = getString(R.string.fetch_fail)
            return
        }
        b.txtFetchStatus.text = getString(R.string.fetch_checking)
        val token = t
        Thread {
            val (ok, name) = DiscordApi.getMe(token)
            runOnUiThread {
                if (done) return@runOnUiThread
                if (ok) {
                    done = true
                    prefs.token = token
                    Toast.makeText(this, getString(R.string.fetch_ok, name), Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    b.txtFetchStatus.text = getString(R.string.fetch_invalid)
                }
            }
        }.start()
    }

    override fun onDestroy() {
        // Anmelde-Spuren im App-Browser löschen (der Token bleibt sicher gespeichert).
        try {
            b.webFetch.stopLoading()
            b.webFetch.clearHistory()
            b.webFetch.clearCache(true)
            b.webFetch.clearFormData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
