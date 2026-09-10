package com.fufcord.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Mini-Bildlader mit Cache (für Asset-Thumbnails aus dem Discord-CDN). */
object ImageLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, Bitmap>(40)

    fun load(url: String, into: ImageView) {
        into.tag = url
        cache.get(url)?.let {
            if (into.tag == url) into.setImageBitmap(it)
            return
        }
        pool.execute {
            try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@execute
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                    cache.put(url, bmp)
                    main.post { if (into.tag == url) into.setImageBitmap(bmp) }
                }
            } catch (e: Exception) { /* Platzhalter bleibt */ }
        }
    }

    fun appAssetUrl(appId: String, assetId: String): String =
        "https://cdn.discordapp.com/app-assets/$appId/$assetId.png"
}
