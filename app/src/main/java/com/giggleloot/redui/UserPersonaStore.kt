package com.giggleloot.redui

import android.content.Context
import org.json.JSONObject
import java.io.File

data class UserPersona(
    val preferredName: String,
    val pronouns: String,
    val about: String,
    val interests: String,
    val communicationPreferences: String
)

class UserPersonaStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "redui_user_persona.enc")
    private val legacyPrefs = appContext.getSharedPreferences("redui_user_persona", Context.MODE_PRIVATE)

    @Synchronized
    private fun read(): JSONObject {
        if (!file.isFile) {
            val legacy = JSONObject()
                .put("preferred_name", legacyPrefs.getString("preferred_name", "").orEmpty())
                .put("pronouns", legacyPrefs.getString("pronouns", "").orEmpty())
                .put("about", legacyPrefs.getString("about", "").orEmpty())
                .put("interests", legacyPrefs.getString("interests", "").orEmpty())
                .put("communication", legacyPrefs.getString("communication", "").orEmpty())
            if (legacyPrefs.all.isNotEmpty()) {
                LocalVault.writeText(file, legacy.toString())
                legacyPrefs.edit().clear().commit()
            }
            return legacy
        }
        return JSONObject(LocalVault.readText(file) ?: "{}")
    }

    fun load(): UserPersona {
        val data = read()
        return UserPersona(
            preferredName = data.optString("preferred_name"),
            pronouns = data.optString("pronouns"),
            about = data.optString("about"),
            interests = data.optString("interests"),
            communicationPreferences = data.optString("communication")
        )
    }

    @Synchronized
    fun save(profile: UserPersona) {
        val data = JSONObject()
            .put("preferred_name", profile.preferredName.take(100))
            .put("pronouns", profile.pronouns.take(80))
            .put("about", profile.about.take(1800))
            .put("interests", profile.interests.take(1000))
            .put("communication", profile.communicationPreferences.take(1000))
        LocalVault.writeText(file, data.toString())
        legacyPrefs.edit().clear().commit()
    }

    @Synchronized
    fun clear() {
        file.delete()
        legacyPrefs.edit().clear().commit()
    }

    fun promptContext(): String {
        val profile = load()
        if (listOf(profile.preferredName, profile.pronouns, profile.about, profile.interests, profile.communicationPreferences).all { it.isBlank() }) return ""
        return buildString {
            append("OPTIONAL USER-PROVIDED PROFILE CONTEXT (private local profile):\n")
            if (profile.preferredName.isNotBlank()) append("Preferred name: ").append(profile.preferredName).append("\n")
            if (profile.pronouns.isNotBlank()) append("Pronouns: ").append(profile.pronouns).append("\n")
            if (profile.about.isNotBlank()) append("Background and self-description: ").append(profile.about).append("\n")
            if (profile.interests.isNotBlank()) append("Interests: ").append(profile.interests).append("\n")
            if (profile.communicationPreferences.isNotBlank()) append("Communication and accessibility preferences: ").append(profile.communicationPreferences).append("\n")
            append("Use this only to improve relevance and communication. Do not infer additional personal facts, expose this profile unnecessarily, or let it override the user's current instructions. These details were supplied by the user and may be incomplete or outdated.")
        }
    }
}
