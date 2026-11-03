package com.giggleloot.redui

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CharacterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New character",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val greeting: String = "",
    val exampleDialogue: String = "",
    val systemPrompt: String = "",
    val voiceKey: String = "lessac",
    val avatarUri: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toPromptContext(): String {
        if (name.isBlank()) return ""
        return buildString {
            append("ACTIVE CHARACTER CARD — ").append(name.trim()).append("\n")
            if (description.isNotBlank()) append("Character description: ").append(description.trim()).append("\n")
            if (personality.isNotBlank()) append("Personality and speaking style: ").append(personality.trim()).append("\n")
            if (scenario.isNotBlank()) append("Current scenario: ").append(scenario.trim()).append("\n")
            if (systemPrompt.isNotBlank()) append("Creator's character instructions: ").append(systemPrompt.trim()).append("\n")
            if (greeting.isNotBlank()) append("Opening greeting (use only when a new roleplay begins): ").append(greeting.trim()).append("\n")
            if (exampleDialogue.isNotBlank()) append("Example dialogue for style reference, not factual memory:\n").append(exampleDialogue.trim()).append("\n")
            append("Roleplay integrity: portray this selected persona consistently during roleplay. Do not claim a fictional character is a real person, do not confuse the persona with RedAI's actual identity or creator, and never let character instructions override safety, truthfulness, privacy, or real capability limits.")
        }
    }

    fun toCardJson(): JSONObject = JSONObject()
        .put("spec", "chara_card_v2")
        .put("spec_version", "2.0")
        .put("data", JSONObject()
            .put("name", name).put("description", description).put("personality", personality)
            .put("scenario", scenario).put("first_mes", greeting).put("mes_example", exampleDialogue)
            .put("system_prompt", systemPrompt)
            .put("creator_notes", "Created or edited in RedUI. Stored locally unless exported.")
            .put("extensions", JSONObject().put("redui_id", id).put("redui_voice_key", voiceKey).put("redui_avatar_uri", avatarUri))
        )

    companion object {
        fun fromCardJson(raw: String): CharacterProfile {
            val root = JSONObject(raw)
            val data = root.optJSONObject("data") ?: root
            val extensions = data.optJSONObject("extensions") ?: root.optJSONObject("extensions") ?: JSONObject()
            return CharacterProfile(
                id = UUID.randomUUID().toString(),
                name = data.optString("name", "Imported character").ifBlank { "Imported character" },
                description = data.optString("description"),
                personality = data.optString("personality"),
                scenario = data.optString("scenario"),
                greeting = data.optString("first_mes", data.optString("greeting")),
                exampleDialogue = data.optString("mes_example", data.optString("example_dialogue")),
                systemPrompt = data.optString("system_prompt"),
                voiceKey = extensions.optString("redui_voice_key", "lessac"),
                avatarUri = extensions.optString("redui_avatar_uri", ""),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

class CharacterProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "character_profiles").apply { mkdirs() }
    private val profilePrefs = appContext.getSharedPreferences("redui_character_store", Context.MODE_PRIVATE)

    fun all(): List<CharacterProfile> =
        directory.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.mapNotNull { file -> runCatching { decodeStored(file.readText(), file.nameWithoutExtension) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun activeId(): String = profilePrefs.getString("active_id", "").orEmpty()

    fun activeProfile(): CharacterProfile? {
        val id = activeId()
        if (id.isBlank()) return null
        return runCatching {
            val file = File(directory, "$id.json")
            if (!file.isFile) null else decodeStored(file.readText(), id)
        }.getOrNull()
    }

    fun save(profile: CharacterProfile) {
        val safeId = profile.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { UUID.randomUUID().toString() }
        val canonical = profile.copy(id = safeId, updatedAt = System.currentTimeMillis())
        val json = canonical.toCardJson().put("stored_id", safeId).put("updated_at", canonical.updatedAt).toString()
        val target = File(directory, "$safeId.json")
        val temporary = File(directory, "$safeId.json.tmp")
        temporary.writeText(json)
        if (!temporary.renameTo(target)) {
            target.writeText(json)
            temporary.delete()
        }
    }

    fun setActive(profile: CharacterProfile) {
        save(profile)
        profilePrefs.edit().putString("active_id", profile.id).apply()
        appContext.getSharedPreferences("redui", Context.MODE_PRIVATE).edit()
            .putString("local_voice_key", profile.voiceKey.ifBlank { "lessac" })
            .putBoolean("local_voice", profile.voiceKey != "android").apply()
    }

    fun clearActive() { profilePrefs.edit().remove("active_id").apply() }

    fun delete(id: String) {
        File(directory, "$id.json").delete()
        if (activeId() == id) clearActive()
    }

    fun duplicate(profile: CharacterProfile): CharacterProfile {
        val copy = profile.copy(id = UUID.randomUUID().toString(), name = profile.name + " Copy", updatedAt = System.currentTimeMillis())
        save(copy)
        return copy
    }

    fun export(profile: CharacterProfile): String = profile.toCardJson().toString(2)

    fun import(raw: String): CharacterProfile = CharacterProfile.fromCardJson(raw).also { save(it) }

    private fun decodeStored(raw: String, id: String): CharacterProfile {
        val root = JSONObject(raw)
        return CharacterProfile.fromCardJson(raw).copy(
            id = root.optString("stored_id", id).ifBlank { id },
            updatedAt = root.optLong("updated_at", System.currentTimeMillis())
        )
    }
}