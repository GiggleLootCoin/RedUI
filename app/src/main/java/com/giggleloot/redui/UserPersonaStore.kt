package com.giggleloot.redui

import android.content.Context

data class UserPersona(
    val preferredName: String,
    val pronouns: String,
    val about: String,
    val interests: String,
    val communicationPreferences: String
)

class UserPersonaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("redui_user_persona", Context.MODE_PRIVATE)

    fun load() = UserPersona(
        preferredName = prefs.getString("preferred_name", "").orEmpty(),
        pronouns = prefs.getString("pronouns", "").orEmpty(),
        about = prefs.getString("about", "").orEmpty(),
        interests = prefs.getString("interests", "").orEmpty(),
        communicationPreferences = prefs.getString("communication", "").orEmpty()
    )

    fun save(profile: UserPersona) {
        prefs.edit()
            .putString("preferred_name", profile.preferredName.take(100))
            .putString("pronouns", profile.pronouns.take(80))
            .putString("about", profile.about.take(1800))
            .putString("interests", profile.interests.take(1000))
            .putString("communication", profile.communicationPreferences.take(1000))
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }

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