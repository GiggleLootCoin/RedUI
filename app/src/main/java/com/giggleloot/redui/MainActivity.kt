package com.giggleloot.redui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val prefs by lazy { getSharedPreferences("redui", MODE_PRIVATE) }

    private val redAiSystemPrompt = """
You are RedAI, created by Red (LittleRedBigSmile).
You are Red's personal local AI assistant inside RedUI.
Your identity is RedAI. Never claim to be TensorFlow, CaffeNet, VGG, ResNet, EfficientNet, another unrelated model, or a different assistant.
Do not invent a task or pretend the user asked for something they did not ask.
Answer the user's actual message directly.
Be natural, useful, honest, concise, and technically capable.
You run locally in RedUI. Do not claim to have internet access, files, tools, sensors, or abilities that are not actually available.
If asked who you are, say you are RedAI, created by Red, running locally in RedUI.
""".trimIndent()

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else "model.gguf" } ?: "model.gguf"
            val f = store.destination(if (name.endsWith(".gguf", true)) name else name + ".gguf")
            contentResolver.openInputStream(uri)?.use { a -> f.outputStream().use { b -> a.copyTo(b) } }
            withContext(Dispatchers.Main) { refresh(f.name) }
        }
    }
    private val pickUserAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("user_avatar", it) }
    private val pickAiAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("ai_avatar", it) }
    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImageUri("wallpaper", it) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = ModelStore(this)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(recognitionListener)
        }
        buildUi()
        applyWallpaper()
        refresh()
    }

    override fun onDestroy() {
        handsFree = false
        speech?.destroy()
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts.language = Locale.getDefault()
        tts.setSpeechRate(prefs.getFloat("tts_speed", 1f))
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
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(4,4,5)) }
        wallpaper = ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; alpha=.16f; visibility=View.GONE }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1,-1))

        val content = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(10))
        }

        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(2),dp(3),dp(2),dp(8)) }
        val brand = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text="RedUI"; textSize=30f; typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
            setTextColor(Color.WHITE); letterSpacing=.01f
        })
        brand.addView(tv("LOCAL AI STUDIO  •  REDAI + YOUR GGUFs",10f,Color.rgb(235,48,58)).apply {
            typeface=Typeface.DEFAULT_BOLD; letterSpacing=.08f
        })
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("PERSONALIZE"){showPersonalize()})
        content.addView(header)

        val hero=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(14))
            background=panel(Color.rgb(17,17,19),Color.rgb(74,27,31),24)
        }
        hero.addView(tv("REDUI",10f,Color.rgb(240,60,69)).apply{typeface=Typeface.DEFAULT_BOLD;letterSpacing=.15f})
        hero.addView(tv("Your AI. Your models. Your way.",20f,Color.WHITE).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(3),0,0)})
        hero.addView(tv("Private by default • local first • no account required",12f,Color.rgb(170,170,176)).apply{setPadding(0,dp(3),0,0)})
        content.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        val modelCard=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=panel(Color.rgb(13,13,15));setPadding(dp(12),dp(9),dp(12),dp(9))}
        val modelHead=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        modelHead.addView(tv("MODEL",10f,Color.GRAY).apply{typeface=Typeface.DEFAULT_BOLD;letterSpacing=.12f},LinearLayout.LayoutParams(0,dp(38),1f))
        modelHead.addView(button("IMPORT GGUF"){pickModel.launch(arrayOf("*/*"))},LinearLayout.LayoutParams(dp(120),dp(42)))
        modelCard.addView(modelHead)
        spinner=Spinner(this)
        modelCard.addView(spinner,LinearLayout.LayoutParams(-1,dp(50)))
        modelCard.addView(button("BEST PERFORMANCE"){
            prefs.edit().putBoolean("best_performance",true).apply()
            Toast.makeText(this,"Galaxy A12 preset saved • 2048 context • 4 threads • streaming",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(4)})
        content.addView(modelCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})

        status=tv("LOCAL • ready",11f,Color.rgb(190,190,195)).apply{background=panel(Color.rgb(10,10,11),Color.rgb(38,38,42),14);setPadding(dp(12),dp(8),dp(12),dp(8))}
        content.addView(status,LinearLayout.LayoutParams(-1,dp(40)).apply{bottomMargin=dp(5)})

        rv=RecyclerView(this).apply{clipToPadding=false;setPadding(0,dp(4),0,dp(5));layoutManager=LinearLayoutManager(this@MainActivity);setBackgroundColor(Color.TRANSPARENT)}
        adapter=ChatAdapter(msgs,prefs);rv.adapter=adapter
        content.addView(rv,LinearLayout.LayoutParams(-1,0,1f))

        val live=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=panel(Color.rgb(12,12,14),Color.rgb(62,27,31),18);setPadding(dp(10),dp(4),dp(6),dp(4))}
        voiceState=tv("HANDS-FREE OFF",11f,Color.rgb(205,205,210)).apply{typeface=Typeface.DEFAULT_BOLD;letterSpacing=.04f}
        live.addView(voiceState,LinearLayout.LayoutParams(0,dp(42),1f))
        live.addView(button("TALK LIVE",true){toggleHandsFree()},LinearLayout.LayoutParams(dp(116),dp(44)))
        content.addView(live,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(6)})

        val composer=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=panel(Color.rgb(17,17,19),Color.rgb(49,49,53),22);setPadding(dp(8),dp(4),dp(6),dp(4))}
        input=EditText(this).apply{hint="Message RedAI…";textSize=16f;background=null;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);maxLines=3;setPadding(dp(7),0,dp(7),0)}
        composer.addView(input,LinearLayout.LayoutParams(0,dp(60),1f))
        composer.addView(button("SEND",true){send(false)},LinearLayout.LayoutParams(dp(78),dp(46)))
        content.addView(composer,LinearLayout.LayoutParams(-1,dp(68)))
        root.addView(content,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
    }

    private fun toggleHandsFree() {
        if (handsFree) {
            handsFree=false; speech?.stopListening(); tts.stop()
            voiceState.text="HANDS-FREE OFF"; status.text="LOCAL • ready"; return
        }
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),9001); return
        }
        if (speech==null) { Toast.makeText(this,"Speech recognition is not available on this phone",Toast.LENGTH_LONG).show(); return }
        if (model==null) { status.text="LOCAL • import RedAI.gguf first"; return }
        handsFree=true; speakReplies=true; startListening()
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode==9001 && results.firstOrNull()==PackageManager.PERMISSION_GRANTED && model!=null) {
            handsFree=true; speakReplies=true; startListening()
        }
    }

    private fun startListening() {
        if(!handsFree || generating || speech==null) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if(!handsFree || generating)return@postDelayed
            try {
                tts.stop()
                val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
                }
                voiceState.text="LISTENING • speak naturally"; speech?.startListening(i)
            } catch(_:Throwable) { voiceState.text="MIC ERROR • tap TALK LIVE again" }
        },350)
    }

    private val recognitionListener=object:RecognitionListener {
        override fun onReadyForSpeech(p:Bundle?){if(handsFree)voiceState.text="LISTENING • go ahead"}
        override fun onBeginningOfSpeech(){if(handsFree){tts.stop();voiceState.text="LISTENING • hearing you"}}
        override fun onRmsChanged(v:Float){}
        override fun onBufferReceived(b:ByteArray?){}
        override fun onEndOfSpeech(){if(handsFree)voiceState.text="THINKING • sending"}
        override fun onError(e:Int){if(handsFree)handler.postDelayed({startListening()},500)}
        override fun onResults(r:Bundle?) {
            val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if(handsFree && text.isNotBlank()){input.setText(text);send(true)} else if(handsFree)startListening()
        }
        override fun onPartialResults(r:Bundle?){
            val text=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if(handsFree && text.isNotBlank())input.setText(text)
        }
        override fun onEvent(t:Int,p:Bundle?){}
    }

    private fun send(fromVoice:Boolean) {
        val prompt=input.text.toString().trim(); val f=model
        if(prompt.isEmpty())return
        if(f==null){status.text="LOCAL • import RedAI.gguf first";return}
        speech?.stopListening(); input.text.clear()
        msgs.add(ChatMessage("user",prompt));adapter.notifyItemInserted(msgs.lastIndex)
        msgs.add(ChatMessage("assistant",""));val ai=msgs.lastIndex;adapter.notifyItemInserted(ai);rv.scrollToPosition(ai)
        generating=true;if(handsFree||fromVoice)voiceState.text="THINKING • RedAI is replying"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                LlamaBridge.updateGenerateParams(.70f,512,.90f,40,1.10f,2048,4,true,true,128,0)
                if(!LlamaBridge.initGenerateModel(f.absolutePath))throw Exception("Could not load GGUF")
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
        val sb=StringBuilder()
        sb.append("<|im_start|>system\n").append(redAiSystemPrompt).append("\n<|im_end|>\n")
        for(m in msgs.dropLast(1).takeLast(8)){
            if(m.text.isBlank())continue
            sb.append("<|im_start|>").append(if(m.role=="user")"user" else "assistant").append("\n")
                .append(m.text).append("\n<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(userPrompt).append("\n<|im_end|>\n<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun cleanForSpeech(s:String)=s.replace(Regex("<think>[\\s\\S]*?</think>"),"").replace(Regex("<\\|[^>]+\\|>"),"").trim()
    private fun speak(s:String){if(!ttsReady||s.isBlank()){if(handsFree)startListening();return};tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"redai-"+System.currentTimeMillis())}
    private fun resumeListening(){if(handsFree&&!generating)handler.postDelayed({startListening()},450)}

    private fun showPersonalize() {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(26),dp(4),dp(26),dp(4))}
        box.addView(button("SET MY PROFILE PICTURE"){pickUserAvatar.launch(arrayOf("image/*"))})
        box.addView(button("SET REDAI PROFILE PICTURE"){pickAiAvatar.launch(arrayOf("image/*"))})
        box.addView(button("SET CHAT WALLPAPER"){pickWallpaper.launch(arrayOf("image/*"))})
        lateinit var voiceButton: Button
        voiceButton = button(if(speakReplies)"VOICE REPLIES: ON" else "VOICE REPLIES: OFF") {
            speakReplies = !speakReplies
            voiceButton.text = if(speakReplies) "VOICE REPLIES: ON" else "VOICE REPLIES: OFF"
        }
        box.addView(voiceButton)
        box.addView(button("TEST REDAI VOICE"){speak("Hello. This is RedAI speaking locally through RedUI.")})
        box.addView(button("RESET WALLPAPER"){prefs.edit().remove("wallpaper").apply();wallpaper.setImageDrawable(null);wallpaper.visibility=View.GONE})
        AlertDialog.Builder(this).setTitle("RedUI • Personalize").setView(box).setPositiveButton("DONE",null).show()
    }

    private fun saveImageUri(key:String,uri:Uri?){
        uri?:return
        try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
        prefs.edit().putString(key,uri.toString()).apply()
        if(key=="wallpaper")applyWallpaper();adapter.notifyDataSetChanged()
    }
    private fun applyWallpaper(){
        val s=prefs.getString("wallpaper",null)?:return
        try{wallpaper.setImageURI(Uri.parse(s));wallpaper.visibility=View.VISIBLE}catch(_:Exception){wallpaper.visibility=View.GONE}
    }

    private fun refresh(selected:String?=null){
        val fs=store.all()
        val names=if(fs.isEmpty())listOf("No GGUF imported") else fs.map{it.name}
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p:AdapterView<*>?){}
            override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){
                model=fs.getOrNull(i);status.text=if(model==null)"LOCAL • import RedAI.gguf" else "LOCAL • "+model!!.name
            }
        }
        val preferred=selected?:fs.firstOrNull{it.name.equals("RedAI.gguf",true)}?.name
        preferred?.let{x->val i=names.indexOf(x);if(i>=0){spinner.setSelection(i);model=fs.getOrNull(i)}}
    }
}