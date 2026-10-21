package com.giggleloot.redui
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class MainActivity:AppCompatActivity(){
private lateinit var store:ModelStore;private lateinit var spinner:Spinner;private lateinit var status:TextView;private lateinit var input:EditText;private lateinit var rv:RecyclerView;private lateinit var adapter:ChatAdapter;private val msgs=mutableListOf<ChatMessage>();private var model:java.io.File?=null
private val pick=registerForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->u?:return@registerForActivityResult;lifecycleScope.launch(Dispatchers.IO){val n=contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else "model.gguf"}? : "model.gguf";val f=store.destination(if(n.endsWith(".gguf",true))n else n+".gguf");contentResolver.openInputStream(u)?.use{a->f.outputStream().use{b->a.copyTo(b)}};withContext(Dispatchers.Main){refresh(f.name)}}}
override fun onCreate(b:Bundle?){super.onCreate(b);store=ModelStore(this);build();refresh()}
private fun build(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,8);setBackgroundColor(Color.rgb(9,9,9))};root.addView(TextView(this).apply{text="RedUI";textSize=28f;setTextColor(Color.WHITE)});root.addView(TextView(this).apply{text="RedAI + any compatible GGUF • local first";setTextColor(Color.LTGRAY)});val row=LinearLayout(this);spinner=Spinner(this);row.addView(spinner,LinearLayout.LayoutParams(0,60,1f));row.addView(Button(this).apply{text="IMPORT GGUF";setOnClickListener{pick.launch(arrayOf("*/*"))}});row.addView(Button(this).apply{text="BEST PERFORMANCE";setOnClickListener{Toast.makeText(this@MainActivity,"2048 context • 4 threads • batch 128 • mmap ON",Toast.LENGTH_LONG).show()}});root.addView(row);status=TextView(this).apply{setTextColor(Color.LTGRAY);setPadding(4,10,4,10)};root.addView(status);rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@MainActivity)};adapter=ChatAdapter(msgs);rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(-1,0,1f));val bottom=LinearLayout(this);input=EditText(this).apply{hint="Message RedAI…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)};bottom.addView(input,LinearLayout.LayoutParams(0,70,1f));bottom.addView(Button(this).apply{text="SEND";setOnClickListener{send()}});root.addView(bottom);setContentView(root)}
private fun refresh(sel:String?=null){val fs=store.all();val names=if(fs.isEmpty())listOf("No GGUF imported")else fs.map{it.name};spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names);spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){ };override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){model=fs.getOrNull(i);status.text=if(model==null)"LOCAL • import a GGUF" else "LOCAL • "+model!!.name}};sel?.let{x->val i=names.indexOf(x);if(i>=0)spinner.setSelection(i)}}
private fun send(){val p=input.text.toString().trim();val f=model;if(p.isEmpty()||f==null)return;input.text.clear();msgs.add(ChatMessage("user",p));adapter.notifyItemInserted(msgs.lastIndex);msgs.add(ChatMessage("assistant",""));val ai=msgs.lastIndex;adapter.notifyItemInserted(ai);lifecycleScope.launch(Dispatchers.IO){try{LlamaBridge.updateGenerateParams(.70f,512,.90f,40,1.10f,2048,4,true,true,128,0);if(!LlamaBridge.initGenerateModel(f.absolutePath))throw Exception("Could not load GGUF");withContext(Dispatchers.Main){status.text="LOCAL • generating"};LlamaBridge.generateStream(p,object:GenStream{override fun onDelta(t:String){runOnUiThread{msgs[ai]=msgs[ai].copy(text=msgs[ai].text+t);adapter.notifyItemChanged(ai);rv.scrollToPosition(ai)}};override fun onComplete(){runOnUiThread{status.text="LOCAL • ready"}};override fun onError(e:String){runOnUiThread{status.text="LOCAL • error: "+e}}})}catch(e:Throwable){withContext(Dispatchers.Main){status.text="LOCAL • "+(e.message?:"error")}}}}
}