package com.giggleloot.redui

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ImageStudioActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private var latestImage: File? = null
    private var selectedExport: File? = null

    private val pickLocalImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        importLocalImage(uri)
    }

    private val exportImage = registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        uri ?: return@registerForActivityResult
        val file = selectedExport ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = LocalVault.readBytes(file) ?: throw IllegalStateException("The selected image no longer exists.")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("The selected image could not be decoded.")
                val png = ByteArrayOutputStream()
                try {
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)) {
                        throw IllegalStateException("The image could not be converted to PNG.")
                    }
                } finally { bitmap.recycle() }
                contentResolver.openOutputStream(uri)?.use { it.write(png.toByteArray()) }
                    ?: throw IllegalStateException("Could not open the selected export location.")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { status.text = "EXPORTED • saved to the location you selected" }
                    .onFailure { status.text = "EXPORT FAILED • " + (it.message ?: "Could not export image.") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        showGallery()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 11f) =
        TextView(this).apply { text = value; textSize = size; setTextColor(Color.LTGRAY) }

    private fun button(value: String, primary: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(if (primary) Color.rgb(184, 24, 35) else Color.rgb(42, 39, 45))
            minHeight = dp(44)
            setOnClickListener { action() }
        }

    private fun buildScreen() {
        window.statusBarColor = Color.rgb(8, 8, 10)
        window.navigationBarColor = Color.rgb(5, 5, 7)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 11))
            setPadding(dp(15))
        }
        root.addView(label("PRIVATE IMAGE LIBRARY", 22f).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(label(
            "Your images stay in local app storage and are encrypted at rest. Import images, view your private gallery, or export a copy. For strict privacy, choose a file stored on this device; cloud storage providers may handle their own files independently of RedUI.",
            12f
        ).apply { setPadding(0, dp(5), 0, dp(9)) })

        root.addView(label(
            "AI image generation is intentionally unavailable in this offline edition. No prompts or source images are uploaded to an external image service.",
            11f
        ).apply {
            setTextColor(Color.rgb(230, 150, 150))
            setPadding(0, dp(3), 0, dp(10))
        })

        root.addView(button("＋ IMPORT IMAGE FROM DEVICE", true) {
            pickLocalImage.launch(arrayOf("image/*"))
        }, LinearLayout.LayoutParams(-1, dp(48)))

        status = label("READY • local image storage", 11f)
        root.addView(status.apply { setPadding(0, dp(10), 0, dp(8)) })

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(20, 19, 23))
            contentDescription = "Selected private image"
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(3)
            bottomMargin = dp(8)
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(button("EXPORT COPY") { exportLatest() }, LinearLayout.LayoutParams(0, dp(45), 1f))
        actions.addView(button("REFRESH GALLERY") { showGallery() }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(7) })
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(button("DELETE SELECTED IMAGE") { deleteSelected() }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(button("DONE") { finish() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
        setContentView(root)
    }

    private fun importLocalImage(uri: Uri) {
        status.text = "IMPORTING • encrypting image locally"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = readBounded(contentResolver, uri, 20_000_000)
                val check = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalArgumentException("The selected file is not a readable image.")
                check.recycle()
                val folder = File(filesDir, "generated_images").apply { mkdirs() }
                val output = File(folder, "LocalImage_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(8) + ".enc")
                LocalVault.writeBytes(output, bytes)
                output
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { file ->
                    latestImage = file
                    loadPreview(file)
                    status.text = "IMPORTED • encrypted in local storage"
                }.onFailure {
                    status.text = "IMPORT FAILED • " + (it.message ?: "The image could not be imported.")
                }
            }
        }
    }

    private fun readBounded(resolver: ContentResolver, uri: Uri, limit: Int): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Could not open the selected image.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw IllegalArgumentException("The selected image is larger than the 20 MB import limit.")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun loadPreview(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                val bytes = LocalVault.readBytes(file) ?: return@runCatching null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bitmap == null) {
                    preview.setImageDrawable(null)
                    status.text = "IMAGE UNAVAILABLE • encrypted file could not be opened"
                } else {
                    preview.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun exportLatest() {
        val file = latestImage
        if (file == null || !file.isFile) {
            Toast.makeText(this, "Import or select an image from the gallery first.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedExport = file
        exportImage.launch(file.nameWithoutExtension + ".png")
    }

    private fun showGallery() {
        val folder = File(filesDir, "generated_images")
        val files = folder.listFiles { file ->
            file.isFile && (file.extension.lowercase() in setOf("enc", "png", "jpg", "jpeg", "webp"))
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        if (files.isEmpty()) {
            latestImage = null
            preview.setImageDrawable(null)
            status.text = "GALLERY EMPTY • import an image stored on this device"
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Private image gallery • ${files.size} images")
            .setItems(files.map { it.nameWithoutExtension }.toTypedArray()) { _, which ->
                latestImage = files[which]
                status.text = "OPENING • decrypting locally"
                loadPreview(files[which])
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun deleteSelected() {
        val file = latestImage
        if (file == null || !file.isFile) {
            Toast.makeText(this, "Select an image first.", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete this image?")
            .setMessage("The selected image will be removed from RedUI's private gallery.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                file.delete()
                latestImage = null
                preview.setImageDrawable(null)
                status.text = "DELETED • local image removed"
            }
            .show()
    }
}
