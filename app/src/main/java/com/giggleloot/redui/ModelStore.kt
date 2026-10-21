package com.giggleloot.redui
import android.content.Context
import java.io.File
class ModelStore(c:Context){private val dir=File(c.filesDir,"models").apply{mkdirs()};fun all()=dir.listFiles()?.filter{it.extension.equals("gguf",true)}?.sortedBy{it.name.lowercase()}?:emptyList();fun destination(n:String)=File(dir,n.replace(Regex("[^A-Za-z0-9._-]"),"_"))}