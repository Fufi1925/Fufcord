package com.fufcord.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    private lateinit var b: ActivityEditorBinding
    private lateinit var prefs: PrefsManager
    private var uploadTarget: String = "large" // oder "small"
    private var pickedBytes: ByteArray? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@registerForActivityResult
            pickedBytes = bytes
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

        b.spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            ActConfig.TYPES.toSortedMap().values.toList())
        b.spStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            ActConfig.STATUS.values.toList())

        loadFields()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        for (et in listOf(b.etName, b.etDetails, b.etState, b.etLarge, b.etSmall,
            b.etBtn1Label, b.etBtn2Label)) et.addTextChangedListener(watcher)

        b.btnUploadLarge.setOnClickListener { uploadTarget = "large"; pickImage.launch("image/*") }
        b.btnUploadSmall.setOnClickListener { uploadTarget = "small"; pickImage.launch("image/*") }
        b.btnRefreshAssets.setOnClickListener { loadAssets() }
        b.btnSave.setOnClickListener { save() }
        updatePreview()
        loadAssets()
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
            PreviewBinder.bind(b.previewCard, readFields(), false)
        } catch (e: Exception) { /* während Tippen egal */ }
    }

    private fun save() {
        val (clean, warns) = ActConfig.sanitize(readFields().toJson())
        prefs.saveAct(clean)
        var msg = "✅ Gespeichert!"
        if (warns.isNotEmpty()) msg += "\n🔧 Auto-Fix:\n• " + warns.take(4).joinToString("\n• ")
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    // ---------------- Bilder-Upload ----------------
    private fun showUploadDialog(bytes: ByteArray) {
        if (!validAppId(prefs.appId)) {
            Toast.makeText(this, "❌ Erst App-ID eintragen! (Hauptmenü → ⚙️)", Toast.LENGTH_LONG).show()
            return
        }
        val lay = LinearLayout(this)
        lay.orientation = LinearLayout.VERTICAL
        lay.setPadding(48, 24, 48, 24)
        val img = ImageView(this)
        img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        img.adjustViewBounds = true
        img.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 500)
        img.scaleType = ImageView.ScaleType.CENTER_INSIDE
        lay.addView(img)
        val et = EditText(this)
        et.hint = "Asset-Name (klein, z.B. logo)"
        val current = if (uploadTarget == "large") b.etLarge.text.toString() else b.etSmall.text.toString()
        if (current.isNotEmpty()) et.setText(current)
        lay.addView(et)
        AlertDialog.Builder(this)
            .setTitle("🖼️ Bild hochladen → Discord-App")
            .setView(lay)
            .setPositiveButton("Hochladen") { _, _ ->
                val name = et.text.toString().trim().lowercase()
                if (!name.matches(Regex("[a-z0-9_]{1,32}"))) {
                    Toast.makeText(this, "❌ Name: nur a-z, 0-9, _ (max 32)!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                doUpload(name, bytes)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun doUpload(name: String, bytes: ByteArray) {
        Toast.makeText(this, "⏳ Lade hoch...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw Exception("Bild defekt")
                // Auf max 1024 skalieren (Discord-Limit)
                val maxSide = maxOf(bmp.width, bmp.height)
                if (maxSide > 1024) {
                    val scale = 1024f / maxSide
                    bmp = Bitmap.createScaledBitmap(bmp,
                        (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                }
                val hasAlpha = bmp.hasAlpha()
                val out = ByteArrayOutputStream()
                if (hasAlpha) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                val mime = if (hasAlpha) "image/png" else "image/jpeg"
                val dataUrl = "data:$mime;base64," +
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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

    private fun loadAssets() {
        b.assetList.removeAllViews()
        if (!validAppId(prefs.appId)) return
        Thread {
            val (ok, assets) = DiscordApi.listAssets(prefs.token, prefs.appId)
            runOnUiThread {
                if (!ok || assets.isEmpty()) return@runOnUiThread
                for ((id, name) in assets) {
                    val row = ItemAssetBinding.inflate(layoutInflater)
                    row.assetName.text = name
                    ImageLoader.load(ImageLoader.appAssetUrl(prefs.appId, id), row.assetThumb)
                    row.assetThumb.setOnClickListener {
                        // Antippen = als großes Bild übernehmen
                        b.etLarge.setText(name)
                        updatePreview()
                        Toast.makeText(this, "'$name' als großes Bild ✓", Toast.LENGTH_SHORT).show()
                    }
                    row.btnAssetDel.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("Löschen?")
                            .setMessage("Asset '$name' aus Discord-App löschen?")
                            .setPositiveButton("Löschen") { _, _ ->
                                Thread {
                                    DiscordApi.deleteAsset(prefs.token, prefs.appId, id)
                                    runOnUiThread { loadAssets() }
                                }.start()
                            }
                            .setNegativeButton("Nein", null)
                            .show()
                    }
                    b.assetList.addView(row.root)
                }
            }
        }.start()
    }
}
