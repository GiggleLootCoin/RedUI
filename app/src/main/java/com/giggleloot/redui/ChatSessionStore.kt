package com.giggleloot.redui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedChat(val id:String,val title:String,val updated:Long,val messages:List<ChatMessage>,val characterId:String = "")

class ChatSessionStore(private val context: Context) {
    private val dir = File(context.filesDir, "saved_chats").apply { mkdirs() }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun safeId(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)

    @Synchronized
    fun save(id:String, messages:List<ChatMessage>, characterId:String = "") {
        if (messages.isEmpty()) return
        val key = safeId(id)
        if (key.isBlank()) throw IllegalArgumentException("Invalid conversation identifier.")
        val title = messages.firstOrNull { it.role == "user" }?.text?.trim()?.replace("\\s+".toRegex()," ")?.take(72)
            ?.ifBlank { "New conversation" } ?: "New conversation"
        val root = JSONObject().put("id",key).put("title",title).put("updated",System.currentTimeMillis())
        val arr = JSONArray()
        messages.forEach { m -> arr.put(JSONObject().put("role",m.role).put("text",m.text)) }
        root.put("messages",arr).put("character_id", characterId)
        LocalVault.writeText(File(dir,"$key.enc"),root.toString())
        File(dir,"$key.json").delete()
    }

    @Synchronized
    fun load(id:String): SavedChat? {
        return try {
            val key = safeId(id)
            if (key.isBlank()) return null
            val encrypted = File(dir,"$key.enc")
            val legacy = File(dir,"$key.json")
            val source = when { encrypted.isFile -> encrypted; legacy.isFile -> legacy; else -> return null }
            val raw = LocalVault.readText(source) ?: return null
            if (source == legacy) {
                LocalVault.writeText(encrypted, raw)
                legacy.delete()
            }
            val root=JSONObject(raw)
            val arr=root.optJSONArray("messages") ?: JSONArray()
            val list=buildList {
                for(i in 0 until arr.length()) {
                    val m=arr.getJSONObject(i)
                    add(ChatMessage(m.optString("role"),m.optString("text")))
                }
            }
            SavedChat(root.optString("id",key),root.optString("title","Conversation"),root.optLong("updated",0),list,root.optString("character_id",""))
        } catch (_:Throwable) { null }
    }

    fun all(characterId:String? = null):List<SavedChat> =
        dir.listFiles { f -> f.isFile && f.extension in setOf("enc","json") }
            ?.map { it.nameWithoutExtension }?.distinct()?.mapNotNull { load(it) }
            ?.filter { characterId == null || it.characterId == characterId }
            ?.sortedByDescending { it.updated } ?: emptyList()

    fun delete(id:String) {
        val key = safeId(id)
        if (key.isBlank()) return
        File(dir,"$key.enc").delete()
        File(dir,"$key.json").delete()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { file ->
            if (file.extension in setOf("enc","json") || file.name.endsWith(".vault-tmp")) file.delete()
        }
    }

    fun displayDate(time:Long):String = if(time<=0) "" else fmt.format(Date(time))
}
