/*
 * Fufcord — https://github.com/Fufi1925/Fufcord
 * Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
 * Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).
 */

package com.fufcord.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fufcord.app.databinding.ActivityEditorBinding
import com.fufcord.app.databinding.ItemAssetBinding
import java.io.ByteArrayOutputStream

/** Presence-Editor: alle Felder + Bilder-Upload + Asset-Liste + Live-Vorschau. */
class EditorActivity : AppCompatActivity() {

    companion object {
        private const val MAX_UPLOAD = 480 * 1024 // Discord mag keine Riesen-Dateien
    }

    private lateinit var b: ActivityEditorBinding
    private lateinit var prefs: PrefsManager
    private var uploadTarget: String = "large" // oder "small"

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@registerForActivityResult
            showUploadDialog(bytes)
        } catch (e: Exception) {
            Toast.makeText(this, "Bildfehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        // FIX: eigene dunkle Spinner-Layouts (System-Layouts waren hell/falsch).
        val typeAdapter = ArrayAdapter(this, R.layout.spinner_item,
            ActConfig.TYPES.toSortedMap().values.toList())
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        b.spType.adapter = typeAdapter
        val statusAdapter = ArrayAdapter(this, R.layout.spinner_item,
            ActConfig.STATUS.values.toList())
        statusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        b.spStatus.adapter = statusAdapter

        loadFields()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        for (et in listOf(b.etName, b.etDetails, b.etState, b.etLarge, b.etSmall,
            b.etBtn1Label, b.etBtn2Label)) et.addTextChangedListener(watcher)

        b.toolbarBack.setOnClickListener { AnimUtils.finish(this) }
        b.btnUploadLarge.setOnClickListener { uploadTarget = "large"; pickImage.launch("image/*") }
        b.btnUploadSmall.setOnClickListener { uploadTarget = "small"; pickImage.launch("image/*") }
        b.btnRefreshAssets.setOnClickListener { loadAssets() }
        b.btnSave.setOnClickListener { save() }
        updatePreview()
        loadAssets()
        AnimUtils.stagger(b.formRoot, 90L)
    }

    private fun loadFields() {
        val a = prefs.loadAct()
        b.etName.setText(a.name)
        b.spType.setSelection(ActConfig.TYPES.keys.sorted().indexOf(a.type).coerceAtLeast(0))
        b.etDetails.setText(a.details)
        b.etState.setText(a.state)
        b.etLarge.setText(a.largeImage)
        b.etLargeText.setText(a.largeText)
        b.etSmall.setText(a.smallImage)
        b.etSmallText.setText(a.smallText)
        b.etStream.setText(a.streamUrl)
        val keys = ActConfig.STATUS.keys.toList()
        b.spStatus.setSelection(keys.indexOf(a.status).coerceAtLeast(0))
        if (a.buttons.isNotEmpty()) {
            b.etBtn1Label.setText(a.buttons[0].label)
            b.etBtn1Url.setText(a.buttons[0].url)
        }
        if (a.buttons.size > 1) {
            b.etBtn2Label.setText(a.buttons[1].label)
            b.etBtn2Url.setText(a.buttons[1].url)
        }
        b.swTime.isChecked = a.useTimestamp
        b.etPartyCur.setText(a.partyCurrent.toString())
        b.etPartyMax.setText(a.partyMax.toString())
    }

    private fun readFields(): ActConfig {
        val typeKeys = ActConfig.TYPES.keys.sorted()
        val statusKeys = ActConfig.STATUS.keys.toList()
        val btns = mutableListOf<ActButton>()
        val l1 = b.etBtn1Label.text.toString().trim()
        val u1 = b.etBtn1Url.text.toString().trim()
        val l2 = b.etBtn2Label.text.toString().trim()
        val u2 = b.etBtn2Url.text.toString().trim()
        if (l1.isNotEmpty() && u1.isNotEmpty()) btns.add(ActButton(l1, u1))
        if (l2.isNotEmpty() && u2.isNotEmpty()) btns.add(ActButton(l2, u2))
        return ActConfig(
            name = b.etName.text.toString().trim(),
            type = typeKeys.getOrElse(b.spType.selectedItemPosition) { 0 },
            details = b.etDetails.text.toString(),
            state = b.etState.text.toString(),
            largeImage = b.etLarge.text.toString().trim(),
            largeText = b.etLargeText.text.toString(),
            smallImage = b.etSmall.text.toString().trim(),
            smallText = b.etSmallText.text.toString(),
            streamUrl = b.etStream.text.toString().trim(),
            buttons = btns,
            useTimestamp = b.swTime.isChecked,
            partyCurrent = b.etPartyCur.text.toString().toIntOrNull() ?: 0,
            partyMax = b.etPartyMax.text.toString().toIntOrNull() ?: 0,
            status = statusKeys.getOrElse(b.spStatus.selectedItemPosition) { "online" }
        )
    }

    private fun updatePreview() {
        try {
            // FIX: Vorschau respektiert den Sicher-Modus (keine falschen Bilder mehr).
            PreviewBinder.bind(b.previewCard, readFields(), prefs.safeMode)
        } catch (e: Exception) { /* während Tippen egal */ }
    }

    private fun save() {
        val (clean, warns) = ActConfig.sanitize(readFields().toJson())
        prefs.saveAct(clean)
        // FIX: Auto-Fix-Hinweise werden IMMER gezeigt (vorher nur bei gestopptem Service).
        var msg = "✅ Gespeichert!"
        if (warns.isNotEmpty()) msg += "\n🔧 Auto-Fix:\n• " + warns.take(4).joinToString("\n• ")
        if (RpcService.isRunning) {
            RpcService.refresh(this)
            msg += "\n⚡ Live aktualisiert!"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        AnimUtils.finish(this)
    }

    // ---------------- Bilder-Upload ----------------
    private fun showUploadDialog(bytes: ByteArray) {
        if (!validAppId(prefs.appId)) {
            Toast.makeText(this, "❌ Erst App-ID eintragen! (Start → Zahnrad)", Toast.LENGTH_LONG).show()
            return
        }
        val lay = LinearLayout(this)
        lay.orientation = LinearLayout.VERTICAL
        lay.setPadding(48, 24, 48, 24)
        val img = ImageView(this)
        // FIX: nur verkleinertes Thumbnail dekodieren (kein OOM bei Riesen-Bildern).
        img.setImageBitmap(decodeSampled(bytes, 512))
        img.adjustViewBounds = true
        img.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 500)
        img.scaleType = ImageView.ScaleType.CENTER_INSIDE
        lay.addView(img)
        val et = EditText(this)
        et.hint = getString(R.string.upload_name_hint)
        val current = if (uploadTarget == "large") b.etLarge.text.toString() else b.etSmall.text.toString()
        if (current.isNotEmpty()) et.setText(current)
        lay.addView(et)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.upload_title))
            .setView(lay)
            .setPositiveButton(getString(R.string.upload_go)) { _, _ ->
                val name = et.text.toString().trim().lowercase()
                if (!name.matches(Regex("[a-z0-9_]{1,32}"))) {
                    Toast.makeText(this, "❌ Name: nur a-z, 0-9, _ (max 32)!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                doUpload(name, bytes)
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun doUpload(name: String, bytes: ByteArray) {
        Toast.makeText(this, "⏳ Lade hoch …", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                // FIX: direkt verkleinert dekodieren (max 1024) statt Vollbild in RAM.
                val bmp = decodeSampled(bytes, 1024) ?: throw Exception("Bild defekt")
                // FIX: Größen-Limit einhalten (Qualitäts-/Größen-Schleife).
                val (data, mime) = compressForDiscord(bmp)
                val dataUrl = "data:$mime;base64," +
                        Base64.encodeToString(data, Base64.NO_WRAP)
                val (ok, res) = DiscordApi.uploadAsset(prefs.token, prefs.appId, name, dataUrl)
                runOnUiThread {
                    if (ok) {
                        if (uploadTarget == "large") b.etLarge.setText(name)
                        else b.etSmall.setText(name)
                        updatePreview()
                        loadAssets()
                        Toast.makeText(this, "✅ '$name' hochgeladen! (5 Min warten)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "❌ Upload fehlgeschlagen: $res", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** Dekodiert ein Bitmap auf max. maxSide herunter (speicherschonend). */
    private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val maxOrig = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxOrig <= 0) return null
            while (maxOrig / sample > maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /** Komprimiert für Discord: PNG bei Transparenz, sonst JPEG-Schleife bis Limit. */
    private fun compressForDiscord(src: Bitmap): Pair<ByteArray, String> {
        var bmp = src
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide > 1024) {
            val s = 1024f / maxSide
            bmp = Bitmap.createScaledBitmap(bmp,
                (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        }
        if (bmp.hasAlpha()) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= MAX_UPLOAD) return out.toByteArray() to "image/png"
        }
        var cur = flatten(bmp)
        var q = 92
        repeat(8) {
            val out = ByteArrayOutputStream()
            cur.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= MAX_UPLOAD) return out.toByteArray() to "image/jpeg"
            q -= 12
            if (q < 45) {
                q = 85
                cur = Bitmap.createScaledBitmap(cur,
                    (cur.width * 0.8).toInt().coerceAtLeast(64),
                    (cur.height * 0.8).toInt().coerceAtLeast(64), true)
            }
        }
        val out = ByteArrayOutputStream()
        cur.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray() to "image/jpeg"
    }

    /** Malt transparente Bereiche weiß (für JPEG ohne schwarze Ränder). */
    private fun flatten(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(flat)
        c.drawColor(Color.WHITE)
        c.drawBitmap(bmp, 0f, 0f, null)
        return flat
    }

    private fun loadAssets() {
        b.assetList.removeAllViews()
        b.txtAssetsHint.visibility = View.GONE
        if (!validAppId(prefs.appId)) {
            b.txtAssetsHint.text = "Erst App-ID eintragen (Start → Zahnrad), dann lädst du hier Bilder hoch."
            b.txtAssetsHint.visibility = View.VISIBLE
            return
        }
        AnimUtils.startSpin(b.btnRefreshAssets)
        Thread {
            val (ok, assets) = DiscordApi.listAssets(prefs.token, prefs.appId)
            runOnUiThread {
                AnimUtils.stopSpin(b.btnRefreshAssets)
                if (!ok) {
                    b.txtAssetsHint.text = "⚠️ Konnte Assets nicht laden (Internet?)."
                    b.txtAssetsHint.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                if (assets.isEmpty()) {
                    b.txtAssetsHint.text = "Noch keine Bilder — lade oben per Upload-Button eins hoch."
                    b.txtAssetsHint.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                for ((id, name) in assets) {
                    val row = ItemAssetBinding.inflate(layoutInflater, b.assetList, false)
                    row.assetName.text = name
                    ImageLoader.load(ImageLoader.appAssetUrl(prefs.appId, id), row.assetThumb)
                    row.root.setOnClickListener { takeAssetDialog(name) }
                    row.btnAssetDel.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.asset_del_title))
                            .setMessage("Asset '$name' aus Discord-App löschen?")
                            .setPositiveButton(getString(R.string.dlg_delete)) { _, _ ->
                                Thread {
                                    DiscordApi.deleteAsset(prefs.token, prefs.appId, id)
                                    runOnUiThread { loadAssets() }
                                }.start()
                            }
                            .setNegativeButton(getString(R.string.dlg_no), null)
                            .show()
                    }
                    b.assetList.addView(row.root)
                }
            }
        }.start()
    }

    /** FIX: Auswahl ob als großes oder kleines Bild übernehmen (vorher nur groß). */
    private fun takeAssetDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.asset_take_title))
            .setItems(arrayOf(
                getString(R.string.asset_take_large),
                getString(R.string.asset_take_small))) { _, which ->
                if (which == 0) b.etLarge.setText(name) else b.etSmall.setText(name)
                updatePreview()
                Toast.makeText(this, "'$name' übernommen ✓", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    @Deprecated("Alte Back-Animation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
