package com.giggleloot.redui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedChat(val id:String,val title:String,val updated:Long,val messages:List<ChatMessage>)

class ChatSessionStore(private val context: Context) {
    private val dir = File(context.filesDir, "saved_chats").apply { mkdirs() }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun save(id:String, messages:List<ChatMessage>) {
        if (messages.isEmpty()) return
        val title = messages.firstOrNull { it.role == "user" }?.text?.trim()?.replace("\\s+".toRegex()," ")?.take(72)
            ?.ifBlank { "New conversation" } ?: "New conversation"
        val root = JSONObject().put("id",id).put("title",title).put("updated",System.currentTimeMillis())
        val arr = JSONArray()
        messages.forEach { m -> arr.put(JSONObject().put("role",m.role).put("text",m.text)) }
        root.put("messages",arr)
        File(dir,"$id.json").writeText(root.toString())
    }

    fun load(id:String): SavedChat? = try {
        val root=JSONObject(File(dir,"$id.json").readText())
        val arr=root.optJSONArray("messages") ?: JSONArray()
        val list=buildList {
            for(i in 0 until arr.length()) {
                val m=arr.getJSONObject(i)
                add(ChatMessage(m.optString("role"),m.optString("text")))
            }
        }
        SavedChat(root.optString("id",id),root.optString("title","Conversation"),root.optLong("updated",0),list)
    } catch (_:Throwable) { null }

    fun all():List<SavedChat> = dir.listFiles { f -> f.extension=="json" }?.mapNotNull { load(it.nameWithoutExtension) }
        ?.sortedByDescending { it.updated } ?: emptyList()

    fun delete(id:String) { try { File(dir,"$id.json").delete() } catch (_:Throwable) {} }
    fun deleteAll() { dir.listFiles()?.forEach { if(it.extension=="json") it.delete() } }
    fun displayDate(time:Long):String = if(time<=0) "" else fmt.format(Date(time))
}