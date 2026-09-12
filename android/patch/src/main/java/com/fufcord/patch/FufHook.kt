/*
 * Fufcord Patch — Xposed-Hook: lädt fufcord.js in Discord.
 * Technik angelehnt an Revenge (ScriptLoader): Nach dem Laden von Discords
 * React-Native-Bundle wird unser Skript per loadScriptFromFile nachgeladen.
 * https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten (MIT-Lizenz).
 */
package com.fufcord.patch

import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

class FufHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        var modulePath: String? = null
        private val injected = AtomicBoolean(false)
        private val targets = setOf(
            "com.discord", "com.discord.canary", "com.discord.ptb",
            "com.discord.development", "com.discord.stable"
        )
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        modulePath = param.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg !in targets && !pkg.startsWith("com.discord.")) return
        // Nur Hauptprozess (Discord startet gern ":phoenix" o.ä. neu)
        if (lpparam.processName != pkg) return
        // RN-Loader-Klasse: neu (ReactInstance) oder alt (CatalystInstanceImpl)
        val loaderCls = try {
            lpparam.classLoader.loadClass("com.facebook.react.runtime.ReactInstance\$loadJSBundle\$1")
        } catch (e: Throwable) {
            try {
                lpparam.classLoader.loadClass("com.facebook.react.bridge.CatalystInstanceImpl")
            } catch (e2: Throwable) {
                return
            }
        }
        hookByName(loaderCls, "loadScriptFromAssets")
        hookByName(loaderCls, "loadScriptFromFile")
    }

    private fun hookByName(cls: Class<*>, name: String) {
        for (m in cls.declaredMethods) {
            if (m.name != name || m.parameterTypes.size != 3) continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        inject(param)
                    }
                })
            } catch (e: Throwable) { /* weiter */
            }
        }
    }

    private fun inject(param: XC_MethodHook.MethodHookParam) {
        if (!injected.compareAndSet(false, true)) return
        try {
            val app = currentApp()
            if (app == null) {
                injected.set(false)
                return
            }
            val jsFile = File(app.filesDir, "fufcord.js")
            val hbcFile = File(app.filesDir, "fufcord.hbc")
            val haveJs = extractAsset(app, "fufcord.js", jsFile)
            val haveHbc = extractAsset(app, "fufcord.hbc", hbcFile)
            val loader = findLoadFromFile(param.thisObject.javaClass)
            if (loader == null) {
                toast(app, "Fufcord: Loader-Methode fehlt (Discord zu neu?)")
                injected.set(false)
                return
            }
            val syncFlag = param.args.size > 2 && param.args[2] == true
            var ok = false
            var err = "kein Bundle gefunden"
            // 1) Plain-JS (versions-sicher), 2) Bytecode-Fallback
            if (haveJs) {
                try {
                    XposedBridge.invokeOriginalMethod(
                        loader, param.thisObject,
                        arrayOf(jsFile.absolutePath, jsFile.absolutePath, syncFlag)
                    )
                    ok = true
                } catch (e: Throwable) {
                    err = e.message ?: "JS-Fehler"
                }
            }
            if (!ok && haveHbc) {
                try {
                    XposedBridge.invokeOriginalMethod(
                        loader, param.thisObject,
                        arrayOf(hbcFile.absolutePath, hbcFile.absolutePath, syncFlag)
                    )
                    ok = true
                } catch (e: Throwable) {
                    err = e.message ?: "HBC-Fehler"
                }
            }
            if (ok) {
                toast(app, "⚡ Fufcord geladen! (Discord → Einstellungen → Fufcord)")
            } else {
                toast(app, "Fufcord-Fehler: $err")
                injected.set(false)
            }
        } catch (e: Throwable) {
            injected.set(false)
        }
    }

    private fun findLoadFromFile(cls: Class<*>): Method? {
        var c: Class<*>? = cls
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == "loadScriptFromFile" && m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == String::class.java
                ) return m
            }
            c = c.superclass
        }
        return null
    }

    private fun currentApp(): Application? {
        return try {
            val at = Class.forName("android.app.ActivityThread")
            val m = at.getDeclaredMethod("currentApplication")
            m.isAccessible = true
            m.invoke(null) as? Application
        } catch (e: Throwable) {
            null
        }
    }

    private fun moduleApk(app: Application): String? {
        modulePath?.let { return it }
        // Fallback: separat installierte Modul-App
        return try {
            app.packageManager.getApplicationInfo("com.fufcord.patch", 0).sourceDir
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Throwable) {
            null
        }
    }

    private fun extractAsset(app: Application, name: String, dest: File): Boolean {
        val apk = moduleApk(app) ?: return false
        return try {
            ZipFile(apk).use { zip ->
                val e = zip.getEntry("assets/$name") ?: return false
                dest.parentFile?.mkdirs()
                zip.getInputStream(e).use { ins -> dest.outputStream().use { ins.copyTo(it) } }
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun toast(app: Application, msg: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                } catch (e: Throwable) {
                }
            }
        } catch (e: Throwable) {
        }
    }
}
