package com.giggleloot.redui

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var store: ModelStore
    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var voiceState: TextView
    private lateinit var input: EditText
    private lateinit var rv: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var wallpaper: ImageView
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val msgs = mutableListOf<ChatMessage>()
    private var model: File? = null
    private var speakReplies = true
    private var handsFree = false
    private var generating = false
    private var ttsReady = false
    private var selectedVoiceName = ""
    private var localVoiceKey = "lessac"
    private var localSpeakerId = 0
    private var localPlayer: MediaPlayer? = null
    private var loadedModelPath = ""
    private var loadedModelPath = ""
    private val prefs by lazy { getSharedPreferences("redui", MODE_PRIVATE) }

    private val redAiSystemPrompt = """
You are RedAI, created by Red (Little Red Big Smile).
You are Red's personal local AI assistant inside RedUI.
Your identity is RedAI. Never claim to be TensorFlow, CaffeNet, VGG, ResNet, EfficientNet, another unrelated model, or a different assistant.
Do not invent a task or pretend the user asked for something they did not ask.
Answer the user's actual message directly.
Be natural, useful, honest, concise, and technically capable.
You run locally in RedUI. Do not claim to have internet access, files, tools, sensors, or abilities that are not actually available.
If asked who you are, say you are RedAI, created by Red, running locally in Red U I. The app name is displayed as RedUI, but when speaking it aloud, pronounce it as Red U I. Never pronounce it as Reduey or any other variation. The creator name is exactly Little Red Big Smile. Never say Little Red Bigger Smile.
""".trimIndent()

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else "model.gguf" } ?: "model.gguf"
            if (!name.endsWith(".gguf", true)) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "RedUI only imports .GGUF model files. ZIP files are not models.", Toast.LENGTH_LONG).show() }
                return@launch
            }
            val f = store.destination(name)
            contentResolver.openInputStream(uri)?.use { a -> f.outputStream().use { b -> a.copyTo(b) } }
            withContext(Dispatchers.Main) { refresh(f.name) }
        }
    }
    private val pickUserAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("user_avatar", it) }
    private val pickAiAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("ai_avatar", it) }
    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("wallpaper", it) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Critical startup path: only construct the visual shell here.
        // Everything that can fail, allocate heavily, or touch disk/network is deferred.
        try {
            store = ModelStore(this)
            buildUi()
        } catch (e: Throwable) {
            showStartupError(e)
            return
        }

        window.decorView.post {
            try { applyWallpaper() } catch (_: Throwable) {}
            try { refresh() } catch (_: Throwable) { status.text = "LOCAL • ready" }

            // Initialize speech only after the first frame is on screen. The previous
            // build never called initLocalVoiceEngine(), so the bundled Piper voices
            // could never become available and every reply silently fell back to Android TTS.
            ttsReady = false
            try { tts = TextToSpeech(this@MainActivity, this@MainActivity) } catch (_: Throwable) { ttsReady = false }
            initLocalVoiceEngine()
        }
    }

    private fun showStartupError(e: Throwable) {
        val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(4, 4, 5))
        }
        view.addView(tv("RedUI", 32f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
        view.addView(tv("The main screen could not be created.", 16f, Color.LTGRAY).apply { setPadding(0, dp(12), 0, dp(8)) })
        view.addView(tv(message, 12f, Color.rgb(235, 80, 88)).apply { setPadding(0, 0, 0, dp(18)) })
        view.addView(button("RESTART REDUI", true) { recreate() })
        setContentView(view)
    }

    override fun onDestroy() {
        handsFree = false
        speech?.destroy()
        stopLocalSpeech()
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        try { LlamaBridge.nativeCancelGenerate() } catch (_: Throwable) {}
        try { LlamaBridge.shutdown() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts.language = Locale.getDefault()
        tts.setSpeechRate(prefs.getFloat("tts_speed", 1f))
        selectedVoiceName = prefs.getString("tts_voice", "") ?: ""
        if (selectedVoiceName.isNotBlank()) tts.voices.firstOrNull { it.name == selectedVoiceName }?.let { tts.voice = it }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { resumeListening() }
            override fun onError(id: String?) { resumeListening() }
        })
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun panel(fill: Int, stroke: Int = Color.rgb(48,48,52), r: Int = 20) =
        GradientDrawable().apply { setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat() }

    private fun button(textValue: String, accent: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = textValue; isAllCaps = false; textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = panel(if (accent) Color.rgb(181,20,29) else Color.rgb(27,27,30),
                if (accent) Color.rgb(239,65,73) else Color.rgb(65,65,70), 15)
            minHeight = dp(44); setPadding(dp(10),0,dp(10),0)
            setOnClickListener { action() }
        }

    private fun tv(s: String, size: Float, color: Int = Color.LTGRAY) =
        TextView(this).apply { text=s; textSize=size; setTextColor(color) }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(3,3,4)) }
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = .12f
            visibility = View.VISIBLE
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1,-1))

        val overlay = View(this).apply {
            setBackgroundColor(Color.rgb(3,3,4))
            alpha = .48f
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1,-1))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(10),dp(14),dp(18))
        }

        // Premium header / identity
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2),dp(3),dp(2),dp(9))
        }

        val mark = TextView(this).apply {
            text = "R"
            textSize = 26f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black",Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = panel(Color.rgb(190,22,32),Color.rgb(245,68,76),17)
        }
        header.addView(mark,LinearLayout.LayoutParams(dp(48),dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11),0,dp(6),0)
        }
        brand.addView(TextView(this).apply {
            text = "RedUI"
            textSize = 28f
            typeface = Typeface.create("sans-serif-black",Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        brand.addView(tv("LOCAL AI STUDIO",10f,Color.rgb(241,61,70)).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
        })
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("SETTINGS"){showSettings()},LinearLayout.LayoutParams(dp(98),dp(44)))
        content.addView(header)

        // Creator strip — intentionally near the top so the app identity is never buried.
        val creator = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.rgb(16,12,13),Color.rgb(92,31,36),18)
            setPadding(dp(13),dp(8),dp(8),dp(8))
        }
        val creatorText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        creatorText.addView(tv("CREATED WITH LOVE",9f,Color.rgb(240,67,76)).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
        })
        creatorText.addView(tv("Little Red Big Smile • Red",13f,Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        creator.addView(creatorText,LinearLayout.LayoutParams(0,-2,1f))
        creator.addView(button("▶ YOUTUBE",true) {
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@Little-Red-Big-Smile")))
        },LinearLayout.LayoutParams(dp(116),dp(44)))
        content.addView(creator,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        // Hero / quick-start card
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18),dp(16),dp(18),dp(16))
            background = panel(Color.rgb(18,17,19),Color.rgb(87,31,36),24)
        }
        hero.addView(tv("REDUI • REDAI",10f,Color.rgb(242,63,72)).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .16f
        })
        hero.addView(tv("Your AI. Your models. Your way.",22f,Color.WHITE).apply {
            typeface = Typeface.create("sans-serif-black",Typeface.BOLD)
            setPadding(0,dp(4),0,0)
        })
        hero.addView(tv("Run RedAI locally, import other GGUF models, talk hands-free, search the web, and personalize the experience.",12f,Color.rgb(185,185,191)).apply {
            setPadding(0,dp(5),0,dp(12))
        })
        val heroButtons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heroButtons.addView(button("IMPORT GGUF",true){
            pickModel.launch(arrayOf("application/octet-stream","application/x-gguf","*/*"))
        },LinearLayout.LayoutParams(0,dp(44),1f))
        heroButtons.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
        heroButtons.addView(button("TALK LIVE"){
            toggleHandsFree()
        },LinearLayout.LayoutParams(0,dp(44),1f))
        hero.addView(heroButtons)
        content.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        // Model card
        val modelCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel(Color.rgb(11,11,13),Color.rgb(45,45,49),20)
            setPadding(dp(12),dp(11),dp(12),dp(11))
        }
        val modelHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val modelTitle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        modelTitle.addView(tv("MODEL",10f,Color.GRAY).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .13f
        })
        modelTitle.addView(tv("Local GGUF engine",12f,Color.WHITE).apply { typeface=Typeface.DEFAULT_BOLD })
        modelHead.addView(modelTitle,LinearLayout.LayoutParams(0,-2,1f))
        modelHead.addView(button("IMPORT",false){
            pickModel.launch(arrayOf("application/octet-stream","application/x-gguf","*/*"))
        },LinearLayout.LayoutParams(dp(92),dp(42)))
        modelCard.addView(modelHead)

        spinner = Spinner(this)
        modelCard.addView(spinner,LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(5)})
        modelCard.addView(button("DELETE SELECTED MODEL"){ deleteSelectedModel() },LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(4)})
        content.addView(modelCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        status = tv("LOCAL • starting…",11f,Color.rgb(205,205,210)).apply {
            typeface = Typeface.DEFAULT_BOLD
            background = panel(Color.rgb(9,9,10),Color.rgb(56,56,61),14)
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        content.addView(status,LinearLayout.LayoutParams(-1,dp(40)).apply{bottomMargin=dp(6)})

        // Chat area
        val chatCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel(Color.argb(190,9,9,11),Color.rgb(38,38,42),20)
            setPadding(dp(8),dp(8),dp(8),dp(8))
        }
        chatCard.addView(tv("CONVERSATION",9f,Color.rgb(151,151,158)).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
            setPadding(dp(5),dp(1),0,dp(3))
        })
        rv = RecyclerView(this).apply {
            clipToPadding = false
            setPadding(dp(2),dp(3),dp(2),dp(4))
            layoutManager = LinearLayoutManager(this@MainActivity)
            setBackgroundColor(Color.TRANSPARENT)
        }
        adapter = ChatAdapter(msgs,prefs)
        rv.adapter = adapter
        chatCard.addView(rv,LinearLayout.LayoutParams(-1,dp(280)))
        content.addView(chatCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        // Hands-free bar
        val live = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.rgb(14,12,13),Color.rgb(76,29,34),18)
            setPadding(dp(10),dp(4),dp(6),dp(4))
        }
        voiceState = tv("HANDS-FREE OFF",11f,Color.rgb(210,210,215)).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .04f
        }
        live.addView(voiceState,LinearLayout.LayoutParams(0,dp(42),1f))
        live.addView(button("TALK LIVE",true){toggleHandsFree()},LinearLayout.LayoutParams(dp(116),dp(44)))
        content.addView(live,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(7)})

        // Composer
        val composer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.rgb(17,17,19),Color.rgb(58,58,63),22)
            setPadding(dp(8),dp(4),dp(6),dp(4))
        }
        input = EditText(this).apply {
            hint = "Message RedAI…"
            textSize = 16f
            background = null
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            maxLines = 3
            setPadding(dp(7),0,dp(7),0)
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(60),1f))
        composer.addView(button("SEND",true){send(false)},LinearLayout.LayoutParams(dp(78),dp(46)))
        content.addView(composer,LinearLayout.LayoutParams(-1,dp(68)).apply{bottomMargin=dp(10)})

        // Utility buttons
        val toolsRow = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        toolsRow.addView(button("WEB SEARCH"){showWebSearch()},LinearLayout.LayoutParams(0,dp(44),1f))
        toolsRow.addView(Space(this),LinearLayout.LayoutParams(dp(7),1))
        toolsRow.addView(button("PERSONALIZE"){showPersonalize()},LinearLayout.LayoutParams(0,dp(44),1f))
        toolsRow.addView(Space(this),LinearLayout.LayoutParams(dp(7),1))
        toolsRow.addView(button("PERFORMANCE"){
            prefs.edit().putBoolean("best_performance",true).apply()
            Toast.makeText(this,"Galaxy A12 performance preset saved • 2048 context • 4 threads • streaming",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(0,dp(44),1f))
        content.addView(toolsRow)

        content.addView(tv("RedAI • Qwen3.5-0.8B + Red’s Ways Of Thinking • Q4_K_M",10f,Color.rgb(130,130,136)).apply {
            gravity = Gravity.CENTER
            setPadding(0,dp(10),0,0)
        })

        scroll.addView(content)
        root.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
    }

    private fun initLocalVoiceEngine() {
        var saved = prefs.getString("local_voice_key", "lessac") ?: "lessac"
        if (saved !in setOf("lessac", "amy", "cori", "alan", "joe", "john", "kathleen", "glados", "android")) saved = "lessac"
        localVoiceKey = saved
        prefs.edit().putString("local_voice_key", saved).putBoolean("local_voice", false).apply()
        status.text = "LOCAL • ready • safe Android voice path"
    }

    private fun speakerIdFor(key: String): Int = 0

    private fun localVoiceLabel(key:String):String = when(key) {
        "lessac" -> "Piper • Lessac sample"
        "amy" -> "Piper • Amy sample"
        "cori" -> "Piper • Cori sample"
        "alan" -> "Piper • Alan sample"
        "joe" -> "Piper • Joe sample"
        "john" -> "Piper • John sample"
        "kathleen" -> "Piper • Kathleen sample"
        "glados" -> "Piper • GLaDOS sample"
        "android" -> "Android system voice"
        else -> key
    }

    private fun selectLocalVoice(key: String, label: String) {
        stopLocalSpeech()
        localVoiceKey = key
        prefs.edit().putString("local_voice_key", key).putBoolean("local_voice", false).apply()
        status.text = "LOCAL • " + label + " • selected"
        if (key == "android") {
            ensureAndroidTts { showAndroidVoiceChoices(availableEnglishVoices()) }
        } else {
            Toast.makeText(this, label + " selected • press TEST REDAI VOICE for the real sample", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocalSpeech() {
        try { localPlayer?.stop() } catch (_: Throwable) {}
        try { localPlayer?.release() } catch (_: Throwable) {}
        localPlayer = null
    }

    private fun createSpeechRecognizer() {
        speech?.destroy(); speech = null
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(recognitionListener)
        }
    }

    private fun toggleHandsFree() {
        if (handsFree) {
            handsFree=false; speech?.stopListening(); if(::tts.isInitialized) tts.stop()
            voiceState.text="HANDS-FREE OFF"; status.text="LOCAL • ready"; return
        }
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),9001); return
        }
        if (speech==null) try { if (SpeechRecognizer.isRecognitionAvailable(this)) createSpeechRecognizer() } catch (_: Throwable) {}
        if (speech==null) { Toast.makeText(this,"Speech recognition is not available on this phone",Toast.LENGTH_LONG).show(); return }
        if (model==null) { status.text="LOCAL • import RedAI.gguf first"; return }
        handsFree=true; speakReplies=true; startListening()
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode==9001 && results.firstOrNull()==PackageManager.PERMISSION_GRANTED && model!=null) { handsFree=true; speakReplies=true; startListening() }
    }

    private fun startListening() {
        if(!handsFree || generating || speech==null) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if(!handsFree || generating)return@postDelayed
            try {
                if(::tts.isInitialized) tts.stop()
                val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,700L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,900L)
                }
                voiceState.text="LISTENING • speak naturally"; speech?.startListening(i)
            } catch(_:Throwable) { voiceState.text="MIC ERROR • tap TALK LIVE again" }
        },350)
    }

    private val recognitionListener=object:RecognitionListener {
        override fun onReadyForSpeech(p:Bundle?){if(handsFree)voiceState.text="LISTENING • go ahead"}
        override fun onBeginningOfSpeech(){if(handsFree){if(::tts.isInitialized)tts.stop();voiceState.text="LISTENING • hearing you"}}
        override fun onRmsChanged(v:Float){}
        override fun onBufferReceived(b:ByteArray?){}
        override fun onEndOfSpeech(){if(handsFree)voiceState.text="THINKING • sending"}
        override fun onError(e:Int){if(handsFree){createSpeechRecognizer();handler.postDelayed({startListening()},700)}}
        override fun onResults(r:Bundle?) {
            val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if(handsFree && text.isNotBlank()){input.setText(text);send(true)} else if(handsFree)startListening()
        }
        override fun onPartialResults(r:Bundle?){val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();if(handsFree&&text.isNotBlank())input.setText(text)}
        override fun onEvent(t:Int,p:Bundle?){}
    }

    private fun send(fromVoice:Boolean) {
        val prompt=input.text.toString().trim(); val f=model
        if(prompt.isEmpty() || generating)return
        if(f==null){status.text="LOCAL • import RedAI.gguf first";return}
        speech?.stopListening(); input.text.clear()
        msgs.add(ChatMessage("user",prompt));adapter.notifyItemInserted(msgs.lastIndex)
        msgs.add(ChatMessage("assistant",""));val ai=msgs.lastIndex;adapter.notifyItemInserted(ai);rv.scrollToPosition(ai)
        generating=true;if(handsFree||fromVoice)voiceState.text="THINKING • RedAI is replying"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.updateGenerateParams(.68f,384,.92f,40,1.10f,2048,4,true,true,128,0)
                if (loadedModelPath != f.absolutePath) {
                    withContext(Dispatchers.Main){status.text="LOCAL • loading • "+f.name}
                    if(!LlamaBridge.initGenerateModel(f.absolutePath))throw Exception("Could not load GGUF")
                    loadedModelPath = f.absolutePath
                    try { LlamaBridge.initMtp(f.absolutePath, 3) } catch (_: Throwable) {}
                }
                withContext(Dispatchers.Main){status.text="LOCAL • generating • "+f.name}
                LlamaBridge.generateStream(buildPrompt(prompt),object:GenStream{
                    override fun onDelta(t:String){runOnUiThread{msgs[ai]=msgs[ai].copy(text=msgs[ai].text+t);adapter.notifyItemChanged(ai);rv.scrollToPosition(ai)}}
                    override fun onComplete(){runOnUiThread{
                        generating=false;status.text="LOCAL • ready • "+f.name
                        if(handsFree)voiceState.text="SPEAKING • you can interrupt"
                        val reply=cleanForSpeech(msgs[ai].text)
                        if(speakReplies)speak(reply) else if(handsFree)startListening()
                    }}
                    override fun onError(e:String){runOnUiThread{generating=false;status.text="LOCAL • error: "+e;if(handsFree)startListening()}}
                })
            } catch(e:Throwable) { withContext(Dispatchers.Main){generating=false;status.text="LOCAL • "+(e.message ?: "error");if(handsFree)startListening()} }
        }
    }

    private fun buildPrompt(userPrompt:String):String {
        val messages = mutableListOf<Pair<String,String>>()
        messages.add("system" to redAiSystemPrompt)
        for (m in msgs.dropLast(1).takeLast(8)) {
            if (m.text.isBlank()) continue
            messages.add((if (m.role == "user") "user" else "assistant") to m.text)
        }
        return LlamaBridge.applyChatTemplate(messages, true)
            ?: run {
                val sb=StringBuilder()
                sb.append("<|im_start|>system\n").append(redAiSystemPrompt).append("<|im_end|>\n")
                for ((role, content) in messages.drop(1)) {
                    sb.append("<|im_start|>").append(role).append("\n").append(content).append("<|im_end|>\n")
                }
                sb.append("<|im_start|>assistant\n")
                sb.toString()
            }
    }

    private fun cleanForSpeech(s:String)=s.replace(Regex("<think>[\\s\\S]*?</think>"),"").replace(Regex("<\\|[^>]+\\|>"),"").trim()
    private fun speak(s:String){
        if(s.isBlank()){if(handsFree)startListening();return}
        speakWithAndroidTts(s)
    }

    private fun speakWithAndroidTts(s:String){
        if(s.isBlank()){if(handsFree)startListening();return}
        try {
            if(!::tts.isInitialized) tts = TextToSpeech(this, this)
            if(!ttsReady){
                status.text = "LOCAL • Android speech engine not ready"
                if(handsFree)startListening()
                return
            }
            val result = tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"redai-"+System.currentTimeMillis())
            if(result == TextToSpeech.ERROR){
                status.text = "LOCAL • Android speech failed"
                if(handsFree)startListening()
            }
        } catch (_:Throwable) {
            status.text = "LOCAL • Android speech unavailable"
            if(handsFree)startListening()
        }
    }

    private fun resumeListening(){if(handsFree&&!generating)handler.postDelayed({startListening()},450)}

    private fun deleteSelectedModel(){val f=model?:return;AlertDialog.Builder(this).setTitle("Delete model?").setMessage("Remove "+f.name+" from RedUI? The original file outside RedUI is not affected.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->f.delete();model=null;refresh()}.show()}

    private fun showSettings(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(4),dp(22),dp(4))}
        box.addView(tv("REDUI SETTINGS",11f,Color.rgb(235,48,58)).apply{typeface=Typeface.DEFAULT_BOLD;letterSpacing=.12f})
        box.addView(button("PERSONALIZE"){showPersonalize()})
        box.addView(button("VOICE CHOICES"){showVoiceChoices()})
        box.addView(button("WEB SEARCH"){showWebSearch()})
        box.addView(button("TALK LIVE"){toggleHandsFree()})
        box.addView(button("BEST PERFORMANCE"){prefs.edit().putBoolean("best_performance",true).apply();Toast.makeText(this,"Galaxy A12 performance preset saved.",Toast.LENGTH_SHORT).show()})
        box.addView(tv("RedAI • Qwen3.5-0.8B + Red’s Ways Of Thinking • Q4_K_M",11f,Color.LTGRAY).apply{setPadding(0,dp(10),0,dp(6))})
        box.addView(tv("Created by Little Red Big Smile — Red",12f,Color.WHITE).apply{setPadding(0,dp(8),0,dp(4));setOnClickListener{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@Little-Red-Big-Smile")))}})
        box.addView(tv("YouTube • Little Red Big Smile",11f,Color.rgb(235,48,58)))
        AlertDialog.Builder(this).setTitle("RedUI • Settings").setView(box).setPositiveButton("DONE",null).show()
    }

    private fun availableEnglishVoices(): List<android.speech.tts.Voice> {
        if (!ttsReady) return emptyList()
        return tts.voices.filter { it.locale.language == Locale.ENGLISH.language }
            .filter { !it.isNetworkConnectionRequired }
            .sortedWith(compareBy<android.speech.tts.Voice>({ it.locale.toLanguageTag() }, { it.name }))
            .distinctBy { it.name }
    }

    private fun showVoiceChoices(){
        ensureAndroidTts {
            val voices = availableEnglishVoices()
            val labels = mutableListOf<String>()
            val keys = mutableListOf<String>()
            voices.forEachIndexed { index, voice ->
                labels.add("LIVE • " + voice.name + " • " + voice.locale.toLanguageTag())
                keys.add("android:" + index)
            }
            labels.add("OFFLINE SAMPLE • Piper • Lessac"); keys.add("lessac")
            labels.add("OFFLINE SAMPLE • Piper • Amy"); keys.add("amy")
            labels.add("OFFLINE SAMPLE • Piper • Cori"); keys.add("cori")
            labels.add("OFFLINE SAMPLE • Piper • Alan"); keys.add("alan")
            labels.add("OFFLINE SAMPLE • Piper • Joe"); keys.add("joe")
            labels.add("OFFLINE SAMPLE • Piper • John"); keys.add("john")
            labels.add("OFFLINE SAMPLE • Piper • Kathleen"); keys.add("kathleen")
            labels.add("OFFLINE SAMPLE • Piper • GLaDOS"); keys.add("glados")
            val saved = prefs.getString("tts_voice", "") ?: ""
            val current = voices.indexOfFirst { it.name == saved }.takeIf { it >= 0 } ?: 0
            AlertDialog.Builder(this).setTitle("RedUI • Working Voice Choices")
                .setSingleChoiceItems(labels.toTypedArray(), current) { d, which ->
                    val key = keys[which]
                    if (key.startsWith("android:")) {
                        val voice = voices[key.substringAfter(":").toInt()]
                        tts.voice = voice
                        selectedVoiceName = voice.name
                        localVoiceKey = "android"
                        prefs.edit().putString("tts_voice", voice.name).putString("local_voice_key", "android").putBoolean("local_voice", false).apply()
                        status.text = "LOCAL • LIVE • " + voice.name
                    } else {
                        selectLocalVoice(key, localVoiceLabel(key))
                    }
                    d.dismiss()
                }
                .setNeutralButton("GET MORE VOICES") { _, _ ->
                    try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                    catch (_:Throwable) { Toast.makeText(this, "Android speech settings are unavailable.", Toast.LENGTH_LONG).show() }
                }
                .setNegativeButton("CLOSE", null).show()
        }
    }

    private fun ensureAndroidTts(onReady: () -> Unit) {
        if (ttsReady) {
            onReady()
            return
        }
        try {
            if (!::tts.isInitialized) {
                tts = TextToSpeech(this, this)
            }
            handler.postDelayed({
                if (ttsReady) onReady()
                else Toast.makeText(this, "Android TTS is still initializing. Try again in a moment.", Toast.LENGTH_SHORT).show()
            }, 700)
        } catch (_: Throwable) {
            ttsReady = false
            Toast.makeText(this, "Android system speech is unavailable.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAndroidVoiceChoices(voices:List<android.speech.tts.Voice>){
        val labels=voices.map{it.name+if(it.isNetworkConnectionRequired)" • online" else " • offline"}.toTypedArray()
        val current=voices.indexOfFirst{it.name==selectedVoiceName}.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Android System Voices").setSingleChoiceItems(labels,current){d,which->val v=voices[which];tts.voice=v;selectedVoiceName=v.name;prefs.edit().putString("tts_voice",v.name).putBoolean("local_voice",false).apply();d.dismiss()}.setNeutralButton("GET MORE VOICES"){_,_->startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.setNegativeButton("CLOSE",null).show()
    }

    private fun showWebSearch(){
        val q=EditText(this).apply{hint="Search the web…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);textSize=16f;setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("RedUI • Web Search").setView(q).setNegativeButton("CLOSE",null).setPositiveButton("SEARCH"){_,_->runWebSearch(q.text.toString().trim())}.show()
    }

    private fun runWebSearch(query:String){
        if(query.isBlank())return
        status.text="WEB • searching…"
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val encoded=java.net.URLEncoder.encode(query,"UTF-8")
                val conn=java.net.URL("https://html.duckduckgo.com/html/?q="+encoded).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout=8000;conn.readTimeout=10000;conn.setRequestProperty("User-Agent","Mozilla/5.0 RedUI")
                val html=conn.inputStream.bufferedReader().use{it.readText()};conn.disconnect()
                val text=android.text.Html.fromHtml(html.replace(Regex("(?is)<script.*?</script>"),"").replace(Regex("(?is)<style.*?</style>"),"").replace(Regex("(?is)<[^>]+>")," ")).toString().replace(Regex("\\s+")," ").trim()
                withContext(Dispatchers.Main){status.text="WEB • results ready";AlertDialog.Builder(this@MainActivity).setTitle("Web results • "+query).setMessage(if(text.length>9000)text.take(9000)+"…" else text).setPositiveButton("OPEN IN BRAVE"){_,_->openWebSearchInBrowser(query)}.setNegativeButton("DONE",null).show()}
            }catch(_:Throwable){withContext(Dispatchers.Main){status.text="WEB • browser fallback";AlertDialog.Builder(this@MainActivity).setTitle("Web search").setMessage("The in-app search service did not return results. You can continue the same search in Brave.").setPositiveButton("OPEN IN BRAVE"){_,_->openWebSearchInBrowser(query)}.setNegativeButton("CLOSE",null).show()}}
        }
    }

    private fun openWebSearchInBrowser(query:String){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+java.net.URLEncoder.encode(query,"UTF-8"))))}

    private fun showPersonalize(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(26),dp(4),dp(26),dp(4))}
        box.addView(button("SET MY PROFILE PICTURE"){pickUserAvatar.launch(arrayOf("image/*"))})
        box.addView(button("SET REDAI PROFILE PICTURE"){pickAiAvatar.launch(arrayOf("image/*"))})
        box.addView(button("SET CHAT WALLPAPER"){pickWallpaper.launch(arrayOf("image/*"))})
        lateinit var voiceButton:Button
        voiceButton=button(if(speakReplies)"VOICE REPLIES: ON" else "VOICE REPLIES: OFF"){speakReplies=!speakReplies;voiceButton.text=if(speakReplies)"VOICE REPLIES: ON" else "VOICE REPLIES: OFF"}
        box.addView(voiceButton)
        box.addView(button("TEST REDAI VOICE"){testSelectedVoice()})
        box.addView(button("RESET WALLPAPER"){prefs.edit().remove("wallpaper").apply();applyWallpaper()})
        AlertDialog.Builder(this).setTitle("RedUI • Personalize").setView(box).setPositiveButton("DONE",null).show()
    }

    private fun testSelectedVoice() {
        try {
            if (localVoiceKey == "android") speakWithAndroidTts("Hello. This is RedAI speaking locally through Red U I.")
            else playBundledVoiceTest(localVoiceKey)
        } catch (_:Throwable) {
            status.text = "LOCAL • voice test failed safely"
        }
    }

    private fun playBundledVoiceTest(key: String) {
        if (key !in setOf("lessac", "amy", "cori", "alan", "joe", "john", "kathleen", "glados")) {
            Toast.makeText(this, "Choose a working Android live voice or a Piper sample first.", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val wav = File(cacheDir, "redui-test-" + key + ".wav")
            try {
                assets.open("testvoices/" + key + ".wav").use { input -> wav.outputStream().use { output -> input.copyTo(output) } }
                withContext(Dispatchers.Main) {
                    try {
                        stopLocalSpeech()
                        val p = MediaPlayer()
                        localPlayer = p
                        p.setDataSource(wav.absolutePath)
                        p.setOnCompletionListener {
                            try { localPlayer?.release() } catch (_:Throwable) {}
                            localPlayer = null; wav.delete(); resumeListening()
                        }
                        p.setOnErrorListener { _, _, _ ->
                            try { localPlayer?.release() } catch (_:Throwable) {}
                            localPlayer = null; wav.delete()
                            status.text = "LOCAL • voice sample playback failed safely"
                            if (handsFree) resumeListening(); true
                        }
                        p.prepare(); p.start()
                        status.text = "LOCAL • " + localVoiceLabel(key) + " • TEST"
                    } catch (_:Throwable) {
                        wav.delete(); status.text = "LOCAL • voice sample playback failed safely"
                    }
                }
            } catch (_:Throwable) {
                wav.delete()
                withContext(Dispatchers.Main) { status.text = "LOCAL • voice sample unavailable" }
            }
        }
    }

    private fun saveImageUri(key:String,uri:Uri?){
        uri?:return
        try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
        prefs.edit().putString(key,uri.toString()).apply()
        if(key=="wallpaper")applyWallpaper()
        if(::adapter.isInitialized)adapter.notifyDataSetChanged()
    }

    private fun applyWallpaper(){
        if(!::wallpaper.isInitialized)return
        val s=prefs.getString("wallpaper",null)
        try{
            if(s.isNullOrBlank()){wallpaper.setImageResource(R.drawable.lobby_hero)}
            else{wallpaper.setImageURI(Uri.parse(s))}
            wallpaper.visibility=View.VISIBLE
        }catch(_:Exception){try{wallpaper.setImageResource(R.drawable.lobby_hero);wallpaper.visibility=View.VISIBLE}catch(_:Throwable){wallpaper.visibility=View.GONE}}
    }

    private fun refresh(selected:String?=null){
        if(!::store.isInitialized||!::spinner.isInitialized)return
        val fs=store.all()
        val names=if(fs.isEmpty())listOf("No GGUF imported") else fs.map{it.name}
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p:AdapterView<*>?){}
            override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){model=fs.getOrNull(i);status.text=if(model==null)"LOCAL • import RedAI.gguf" else "LOCAL • "+model!!.name}
        }
        val preferred=selected?:fs.firstOrNull{it.name.equals("RedAI.gguf",true)}?.name
        preferred?.let{x->val i=names.indexOf(x);if(i>=0){spinner.setSelection(i);model=fs.getOrNull(i)}}
    }
}
