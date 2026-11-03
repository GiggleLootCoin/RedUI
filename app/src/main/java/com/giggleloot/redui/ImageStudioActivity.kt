package com.giggleloot.redui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageStudioActivity : AppCompatActivity() {
    private lateinit var prompt: EditText
    private lateinit var negative: EditText
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var dimensions: Spinner
    private lateinit var steps: SeekBar
    private lateinit var stepsLabel: TextView
    private lateinit var sourceLabel: TextView
    private lateinit var generateButton: Button
    private var sourceUri: Uri? = null
    private var latestImage: File? = null
    private var selectedExport: File? = null
    private var generating = false

    private val pickSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        sourceUri = uri
        sourceLabel.text = "SOURCE IMAGE SELECTED • IMAGE-TO-IMAGE"
    }

    private val exportImage = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri ?: return@registerForActivityResult
        val file = selectedExport ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IllegalStateException("Could not open the selected export location.")
        }.onSuccess { Toast.makeText(this, "Image exported.", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Image export failed.", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
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
        root.addView(label("REDUI • IMAGE STUDIO", 23f).apply {
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(label("Free volunteer-powered generation • text-to-image and image-to-image", 11f).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        prompt = EditText(this).apply {
            hint = "Describe the image you want in detail…"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            textSize = 14f; minLines = 3; maxLines = 6
            setPadding(dp(11)); setBackgroundColor(Color.rgb(30, 28, 34))
        }
        negative = EditText(this).apply {
            hint = "Optional negative prompt (what to avoid)"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            textSize = 13f; minLines = 2; maxLines = 4
            setPadding(dp(11)); setBackgroundColor(Color.rgb(25, 24, 29))
        }
        content.addView(label("IMAGE PROMPT"))
        content.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(9) })
        content.addView(label("NEGATIVE PROMPT"))
        content.addView(negative, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        content.addView(label("ASPECT RATIO", 10f).apply { setPadding(0, dp(12), 0, dp(3)) })
        dimensions = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImageStudioActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Square • 512 × 512", "Portrait • 512 × 768", "Landscape • 768 × 512"))
        }
        content.addView(dimensions, LinearLayout.LayoutParams(-1, dp(48)))
        stepsLabel = label("QUALITY STEPS • 20")
        content.addView(stepsLabel.apply { setPadding(0, dp(11), 0, 0) })
        steps = SeekBar(this).apply {
            max = 20
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    stepsLabel.text = "QUALITY STEPS • " + (10 + value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        content.addView(steps)
        content.addView(button("CHOOSE SOURCE IMAGE • OPTIONAL", false) {
            pickSource.launch(arrayOf("image/*"))
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        sourceLabel = label("NO SOURCE • TEXT-TO-IMAGE")
        content.addView(sourceLabel.apply { setPadding(0, dp(4), 0, dp(8)) })
        generateButton = button("GENERATE IMAGE", true) { confirmPrivacyAndGenerate() }
        content.addView(generateButton, LinearLayout.LayoutParams(-1, dp(50)))
        status = label("Ready • generation requires an internet connection.")
        content.addView(status.apply { setPadding(0, dp(9), 0, dp(8)) })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(20, 19, 23))
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(250)))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(button("EXPORT IMAGE") { exportLatest() }, LinearLayout.LayoutParams(0, dp(45), 1f))
        actions.addView(button("GALLERY") { showGallery() }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(7) })
        content.addView(actions, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        content.addView(label("Privacy: prompts and any source image are sent to the external AI Horde volunteer network for processing. Anonymous requests need no account or API key, but have the lowest queue priority. Requests and images are processed by external volunteer workers; anonymous generations may be shared under AI Horde community rules. Do not send private, sensitive or identifying images. Results may take time or fail when no suitable worker is available.", 10f).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("DONE") { finish() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun confirmPrivacyAndGenerate() {
        if (generating) return
        val promptText = prompt.text.toString().trim()
        if (promptText.isBlank()) {
            prompt.error = "Describe the image first."
            return
        }
        val disclosure = if (sourceUri == null) {
            "Your image prompt and negative prompt will be sent over HTTPS to AI Horde and processed by independent volunteer workers. Anonymous use is free and needs no account, but queue priority is lowest and anonymous generations may be shared under AI Horde's community rules. Do not send private or identifying material. Continue?"
        } else {
            "Your prompt and selected source image will be sent over HTTPS to AI Horde and processed by independent volunteer workers. Do not send private or identifying images. Anonymous use is free, with the lowest queue priority. Requests and source images go to volunteer workers, and anonymous generations may be shared under AI Horde's community rules. Do not send private or identifying material. Continue?"
        }
        AlertDialog.Builder(this).setTitle("External image generation")
            .setMessage(disclosure)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CONTINUE") { _, _ -> generate() }
            .show()
    }

    private fun generate() {
        val sizes = when (dimensions.selectedItemPosition) {
            1 -> 512 to 768
            2 -> 768 to 512
            else -> 512 to 512
        }
        generating = true
        generateButton.isEnabled = false
        status.text = "Preparing request…"
        val promptValue = prompt.text.toString()
        val negativeValue = negative.text.toString()
        val source = sourceUri
        val stepValue = 10 + steps.progress
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = AiHordeImageService(this@ImageStudioActivity).generate(
                    promptValue, negativeValue, sizes.first, sizes.second, stepValue, source
                ) { message -> runOnUiThread { status.text = message } }
                withContext(Dispatchers.Main) {
                    latestImage = result.file
                    val bitmap = BitmapFactory.decodeFile(result.file.absolutePath)
                    preview.setImageBitmap(bitmap)
                    status.text = "COMPLETE • saved to RedUI gallery • queue position " +
                        (if (result.queuePosition >= 0) result.queuePosition else "not reported") +
                        (if (result.waitSeconds >= 0) " • estimated wait " + result.waitSeconds + "s" else "")
                    Toast.makeText(this@ImageStudioActivity, "Image generated and saved privately.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    status.text = "NOT COMPLETED • " + (error.message ?: "Image generation failed.")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    generating = false
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun exportLatest() {
        val file = latestImage
        if (file == null || !file.isFile) {
            Toast.makeText(this, "Generate or select an image from the gallery first.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedExport = file
        exportImage.launch(file.name)
    }

    private fun showGallery() {
        val folder = File(filesDir, "generated_images")
        val files = folder.listFiles { file -> file.isFile && file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            AlertDialog.Builder(this).setTitle("RedUI image gallery")
                .setMessage("No generated images have been saved on this phone yet.")
                .setPositiveButton("CLOSE", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("RedUI gallery • " + files.size + " images")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                latestImage = files[which]
                preview.setImageBitmap(BitmapFactory.decodeFile(files[which].absolutePath))
                status.text = "GALLERY • " + files[which].name
            }
            .setNegativeButton("CLOSE", null).show()
    }
}