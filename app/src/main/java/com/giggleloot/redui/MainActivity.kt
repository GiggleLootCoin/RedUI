package com.giggleloot.redui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var store: ModelStore
    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var rv: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var wallpaper: ImageView
    private lateinit var tts: TextToSpeech
    private val msgs = mutableListOf<ChatMessage>()
    private var model: java.io.File? = null
    private var speakReplies = false
    private var ttsReady = false
    private val prefs by lazy { getSharedPreferences("redui", MODE_PRIVATE) }

    private val redAiSystemPrompt = """
You are RedAI, created by Red (LittleRedBigSmile).
You are Red's personal local AI assistant inside RedUI.
Your identity is RedAI. Never claim to be TensorFlow, CaffeNet, VGG, ResNet, EfficientNet, another unrelated model, or a different assistant.
Do not invent a task or pretend the user asked for something they did not ask.
Answer the user's actual message directly.
Be natural, useful, honest, concise, and technically capable.
You run locally in RedUI. Do not claim to have internet access, files, tools, sensors, or abilities that are not actually available in the current chat.
If asked who you are, say you are RedAI, created by Red, running locally in RedUI.
""".trimIndent()

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        u ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val name = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else "model.gguf" } ?: "model.gguf"
            val f = store.destination(if (name.endsWith(".gguf", true)) name else name + ".gguf")
            contentResolver.openInputStream(u)?.use { a -> f.outputStream().use { b -> a.copyTo(b) } }
            withContext(Dispatchers.Main) { refresh(f.name) }
        }
    }

    private val pickUserAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> saveImageUri("user_avatar", u) }
    private val pickAiAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> saveImageUri("ai_avatar", u) }
    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> saveImageUri("wallpaper", u) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = ModelStore(this)
        tts = TextToSpeech(this, this)
        build()
        applyWallpaper()
        refresh()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(prefs.getFloat("tts_speed", 1.0f))
        }
    }

    private fun panel(fill: Int = Color.rgb(18,18,18), stroke: Int = Color.rgb(45,45,45), radius: Float = 22f): GradientDrawable = GradientDrawable().apply { setColor(fill); setStroke(1, stroke); cornerRadius = radius }\n\n    private fun accentButton(label: String, onClick: () -> Unit): Button = Button(this).apply {\n        text = label\n        isAllCaps = false\n        setTextColor(Color.WHITE)\n        background = panel(Color.rgb(28,28,28), Color.rgb(70,25,28), 18f)\n        setOnClickListener { onClick() }\n    }\n\n    private fun build() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 5, 6)) }
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.22f
            visibility = View.GONE
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 12)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val titleRow = LinearLayout(this).apply {\n            setPadding(4, 4, 4, 10)\n        }
        val title = TextView(this).apply {
            text = "RedUI"
            textSize = 28f
            setTextColor(Color.WHITE)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, 60, 1f))
        titleRow.addView(Button(this).apply {
            text = "PERSONALIZE"
            setOnClickListener { showPersonalize() }
        })
        content.addView(titleRow)

        content.addView(TextView(this).apply {
            text = "RedAI + any compatible GGUF • local first"
            setTextColor(Color.LTGRAY)
        })

        val row = LinearLayout(this).apply {\n            background = panel()\n            setPadding(10, 8, 10, 8)\n        }
        spinner = Spinner(this)
        row.addView(spinner, LinearLayout.LayoutParams(0, 60, 1f))
        row.addView(Button(this).apply {
            text = "IMPORT GGUF"
            setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        })
        row.addView(Button(this).apply {
            text = "BEST PERFORMANCE"
            setOnClickListener {
                prefs.edit().putBoolean("best_performance", true).apply()
                Toast.makeText(this@MainActivity, "Best-performance preset saved: 2048 context • 4 threads • batch 128 • mmap • streaming", Toast.LENGTH_LONG).show()
            }
        })
        content.addView(row)

        status = TextView(this).apply {\n            background = panel(Color.rgb(12,12,13), Color.rgb(40,40,40), 16f)\n            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(4, 10, 4, 10)
        }
        content.addView(status)

        rv = RecyclerView(this).apply {\n            clipToPadding = false\n            setPadding(0, 10, 0, 10)
            layoutManager = LinearLayoutManager(this@MainActivity)
            setBackgroundColor(Color.TRANSPARENT)
        }
        adapter = ChatAdapter(msgs, prefs)
        rv.adapter = adapter
        content.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {\n            background = panel(Color.rgb(16,16,17), Color.rgb(48,48,48), 24f)\n            setPadding(10, 6, 6, 6)\n        }
        input = EditText(this).apply {
            hint = "Talk to RedAI…"\n            textSize = 16f\n            background = null
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(false)
        }
        bottom.addView(input, LinearLayout.LayoutParams(0, 70, 1f))
        bottom.addView(accentButton("Send") { send() })
        content.addView(bottom)

        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun showPersonalize() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        box.addView(Button(this).apply { text = "SET MY PROFILE PICTURE"; setOnClickListener { pickUserAvatar.launch(arrayOf("image/*")) } })
        box.addView(Button(this).apply { text = "SET REDAI PROFILE PICTURE"; setOnClickListener { pickAiAvatar.launch(arrayOf("image/*")) } })
        box.addView(Button(this).apply { text = "SET CHAT WALLPAPER"; setOnClickListener { pickWallpaper.launch(arrayOf("image/*")) } })
        box.addView(Button(this).apply {
            text = if (speakReplies) "VOICE: ON" else "VOICE: OFF"
            setOnClickListener {
                speakReplies = !speakReplies
                (it as Button).text = if (speakReplies) "VOICE: ON" else "VOICE: OFF"
            }
        })
        box.addView(Button(this).apply {
            text = "TEST REDAI VOICE"
            setOnClickListener { speak("Hello. This is RedAI speaking locally through Android text to speech.") }
        })
        box.addView(Button(this).apply {
            text = "RESET WALLPAPER"
            setOnClickListener {
                prefs.edit().remove("wallpaper").apply()
                wallpaper.setImageDrawable(null)
                wallpaper.visibility = View.GONE
            }
        })
        AlertDialog.Builder(this).setTitle("RedUI Personalization & Voice").setView(box).setPositiveButton("DONE", null).show()
    }

    private fun saveImageUri(key: String, uri: Uri?) {
        uri ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        prefs.edit().putString(key, uri.toString()).apply()
        if (key == "wallpaper") applyWallpaper()
        adapter.notifyDataSetChanged()
    }

    private fun applyWallpaper() {
        val s = prefs.getString("wallpaper", null) ?: return
        try {
            wallpaper.setImageURI(Uri.parse(s))
            wallpaper.visibility = View.VISIBLE
        } catch (_: Exception) {
            wallpaper.visibility = View.GONE
        }
    }

    private fun refresh(sel: String? = null) {
        val fs = store.all()
        val names = if (fs.isEmpty()) listOf("No GGUF imported") else fs.map { it.name }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                model = fs.getOrNull(i)
                status.text = if (model == null) "LOCAL • import a GGUF" else "LOCAL • " + model!!.name
            }
        }
        val preferred = sel ?: fs.firstOrNull { it.name.equals("RedAI.gguf", true) }?.name
        preferred?.let { x ->
            val i = names.indexOf(x)
            if (i >= 0) {
                spinner.setSelection(i)
                model = fs.getOrNull(i)
                status.text = if (model == null) "LOCAL • import a GGUF" else "LOCAL • " + model!!.name
            }
        }
    }

    private fun buildPrompt(userPrompt: String): String {
        val history = msgs.dropLast(1).takeLast(8)
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(redAiSystemPrompt)
        sb.append("\n<|im_end|>\n")
        for (m in history) {
            val role = if (m.role == "user") "user" else "assistant"
            if (m.text.isBlank()) continue
            sb.append("<|im_start|>").append(role).append("\n")
            sb.append(m.text)
            sb.append("\n<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n")
        sb.append(userPrompt)
        sb.append("\n<|im_end|>\n<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun send() {
        val prompt = input.text.toString().trim()
        val f = model
        if (prompt.isEmpty()) return
        if (f == null) {
            status.text = "LOCAL • import RedAI.gguf or another compatible GGUF first"
            return
        }
        input.text.clear()
        msgs.add(ChatMessage("user", prompt))
        adapter.notifyItemInserted(msgs.lastIndex)
        msgs.add(ChatMessage("assistant", ""))
        val ai = msgs.lastIndex
        adapter.notifyItemInserted(ai)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.updateGenerateParams(.70f, 512, .90f, 40, 1.10f, 2048, 4, true, true, 128, 0)
                if (!LlamaBridge.initGenerateModel(f.absolutePath)) throw Exception("Could not load GGUF")
                withContext(Dispatchers.Main) { status.text = "LOCAL • generating • " + f.name }
                LlamaBridge.generateStream(buildPrompt(prompt), object : GenStream {
                    override fun onDelta(t: String) {
                        runOnUiThread {
                            msgs[ai] = msgs[ai].copy(text = msgs[ai].text + t)
                            adapter.notifyItemChanged(ai)
                            rv.scrollToPosition(ai)
                        }
                    }
                    override fun onComplete() {
                        runOnUiThread {
                            status.text = "LOCAL • ready • " + f.name
                            if (speakReplies) speak(cleanForSpeech(msgs[ai].text))
                        }
                    }
                    override fun onError(e: String) {
                        runOnUiThread { status.text = "LOCAL • error: " + e }
                    }
                })
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { status.text = "LOCAL • " + (e.message ?: "error") }
            }
        }
    }

    private fun cleanForSpeech(text: String): String {
        return text.replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("<\\|[^>]+\\|>"), "")
            .trim()
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "redai-" + System.currentTimeMillis())
    }
}
