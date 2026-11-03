package com.giggleloot.redui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.util.UUID

class CharacterStudioActivity : AppCompatActivity() {
    private lateinit var store: CharacterProfileStore
    private lateinit var list: LinearLayout
    private var pendingAvatar = ""
    private var exportProfile: CharacterProfile? = null

    private val importCard = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(2_000_000) }
                ?: throw IllegalStateException("The selected character file could not be read.")
        }.onSuccess { raw ->
            runCatching { store.import(raw) }
                .onSuccess { Toast.makeText(this, "Character imported: \${it.name}", Toast.LENGTH_LONG).show(); render() }
                .onFailure { Toast.makeText(this, "That character file could not be imported.", Toast.LENGTH_LONG).show() }
        }.onFailure { Toast.makeText(this, "The selected file could not be opened.", Toast.LENGTH_LONG).show() }
    }

    private val exportCard = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        val profile = exportProfile ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.export(profile)) }
                ?: throw IllegalStateException("The destination could not be opened.")
        }.onSuccess { Toast.makeText(this, "Character card exported.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Character card export failed.", Toast.LENGTH_LONG).show() }
    }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        pendingAvatar = uri.toString()
        Toast.makeText(this, "Portrait selected. Save the character to keep it.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CharacterProfileStore(this)
        buildScreen()
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = Color.LTGRAY) =
        TextView(this).apply { text = value; textSize = size; setTextColor(color) }

    private fun action(label: String, primary: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(if (primary) Color.rgb(180, 22, 32) else Color.rgb(39, 37, 43))
            minHeight = dp(44)
            setOnClickListener { onClick() }
        }

    private fun buildScreen() {
        window.statusBarColor = Color.rgb(8, 8, 10)
        window.navigationBarColor = Color.rgb(5, 5, 7)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 11))
            setPadding(dp(16))
        }
        root.addView(text("CHARACTER STUDIO", 23f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
        root.addView(text("Create, edit, duplicate, import and export portable character cards. Profiles stay on this phone unless you export them.", 12f).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(action("＋ NEW CHARACTER", true) { openEditor(null) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(action("IMPORT CARD") { importCard.launch(arrayOf("application/json", "text/plain", "*/*")) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(7) })
        root.addView(top)
        root.addView(action("EDIT MY USER PROFILE • PRIVATE AND OPTIONAL") { editUserProfile() },
            LinearLayout.LayoutParams(-1, dp(45)).apply { topMargin = dp(8) })
        root.addView(text("YOUR CHARACTERS", 10f, Color.rgb(220, 55, 65)).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(17), 0, dp(8))
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(action("CLEAR ACTIVE") { store.clearActive(); render() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(action("DONE", true) { finish() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(7) })
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val active = store.activeId()
        val profiles = store.all()
        if (profiles.isEmpty()) {
            list.addView(text("No character cards yet. Create one to give RedUI a distinct roleplay persona, voice and scenario.", 13f))
            return
        }
        profiles.forEach { profile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12))
                setBackgroundColor(Color.rgb(24, 22, 27))
            }
            val hero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val avatar = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(42, 39, 45))
            }
            val avatarBitmap = store.loadAvatar(profile)
            if (avatarBitmap != null) avatar.setImageBitmap(avatarBitmap)
            else avatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            hero.addView(avatar, LinearLayout.LayoutParams(dp(66), dp(66)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            info.addView(text(profile.name + if (profile.id == active) "  •  ACTIVE" else "", 16f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
            info.addView(text(profile.description.ifBlank { profile.personality }.take(220), 11f).apply {
                setPadding(0, dp(5), 0, dp(3))
            })
            hero.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(hero)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(action(if (profile.id == active) "ACTIVE" else "USE") {
                store.setActive(profile)
                render()
                Toast.makeText(this@CharacterStudioActivity, "\${profile.name} is now active.", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(action("EDIT") { openEditor(profile) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
            row.addView(action("MORE") { showMore(profile) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
            card.addView(row)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun editUserProfile() {
        val store = UserPersonaStore(this)
        val current = store.load()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        fun add(label: String, value: String, hint: String, multiline: Boolean): EditText {
            content.addView(text(label, 10f, Color.rgb(232, 70, 78)).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(9), 0, dp(4))
            })
            return EditText(this).apply {
                setText(value)
                this.hint = hint
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                textSize = 14f
                setBackgroundColor(Color.rgb(34, 32, 38))
                setPadding(dp(10))
                minLines = if (multiline) 2 else 1
                maxLines = if (multiline) 5 else 1
                isSingleLine = !multiline
            }.also { content.addView(it) }
        }
        val preferredName = add("PREFERRED NAME", current.preferredName, "How would you like this assistant to address you?", false)
        val pronouns = add("PRONOUNS • OPTIONAL", current.pronouns, "Only add these if you want to.", false)
        val about = add("ABOUT ME • OPTIONAL", current.about, "Background or a short description you choose to share.", true)
        val interests = add("INTERESTS • OPTIONAL", current.interests, "Creative work, subjects, skills or hobbies.", true)
        val communication = add("HOW I PREFER TO COMMUNICATE", current.communicationPreferences, "Tone, detail, accessibility or formatting preferences.", true)
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("My private user profile")
            .setMessage("These details are stored locally on this phone and added as context to your conversations. Leave anything blank that you do not want to share.")
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("CLEAR PROFILE") { _, _ -> store.clear(); Toast.makeText(this, "User profile cleared.", Toast.LENGTH_SHORT).show() }
            .setPositiveButton("SAVE") { _, _ ->
                store.save(UserPersona(
                    preferredName.text.toString().trim(),
                    pronouns.text.toString().trim(),
                    about.text.toString().trim(),
                    interests.text.toString().trim(),
                    communication.text.toString().trim()
                ))
                Toast.makeText(this, "Private user profile saved.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showMore(profile: CharacterProfile) {
        val choices = arrayOf("Duplicate", "Export character card", "Delete")
        AlertDialog.Builder(this).setTitle(profile.name).setItems(choices) { _, which ->
            when (which) {
                0 -> { store.duplicate(profile); render() }
                1 -> {
                    exportProfile = profile
                    exportCard.launch((profile.name.ifBlank { "character" }.replace(Regex("[^A-Za-z0-9_-]"), "_")) + ".json")
                }
                2 -> AlertDialog.Builder(this).setTitle("Delete \${profile.name}?")
                    .setMessage("This removes the local character profile. It does not delete chats or exported files.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ -> store.delete(profile.id); render() }.show()
            }
        }.show()
    }

    private fun field(label: String, value: String, hint: String, multiline: Boolean = true): EditText {
        val box = EditText(this).apply {
            setText(value)
            this.hint = hint
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 14f
            setBackgroundColor(Color.rgb(34, 32, 38))
            setPadding(dp(10))
            minLines = if (multiline) 2 else 1
            maxLines = if (multiline) 6 else 1
            isSingleLine = !multiline
        }
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(text(label, 10f, Color.rgb(232, 70, 78)).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(9), 0, dp(4))
        })
        wrapper.addView(box)
        box.tag = wrapper
        return box
    }

    private fun openEditor(existing: CharacterProfile?) {
        pendingAvatar = existing?.avatarUri.orEmpty()
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
        }
        fun add(label: String, value: String, hint: String, multiline: Boolean = true): EditText {
            val f = field(label, value, hint, multiline)
            content.addView(f.tag as LinearLayout)
            return f
        }
        val name = add("CHARACTER NAME", existing?.name.orEmpty(), "e.g. A thoughtful British detective", false)
        val description = add("DESCRIPTION / BACKGROUND", existing?.description.orEmpty(), "Who is this character? What do they know?")
        val personality = add("PERSONALITY & VOICE", existing?.personality.orEmpty(), "Temperament, humour, vocabulary, speaking style and boundaries.")
        val scenario = add("SCENARIO", existing?.scenario.orEmpty(), "Where are we? What is happening? What is the relationship or context?")
        val greeting = add("FIRST MESSAGE", existing?.greeting.orEmpty(), "The opening line when a fresh roleplay starts.")
        val examples = add("EXAMPLE DIALOGUE", existing?.exampleDialogue.orEmpty(), "Optional example exchanges to guide dialogue style.")
        val instructions = add("ADVANCED SYSTEM INSTRUCTIONS", existing?.systemPrompt.orEmpty(), "Extra character instructions. Core safety, privacy and truth rules remain in force.")
        val voiceNames = listOf(
            "RedUI Clear • Lessac" to "lessac", "British • Cori" to "cori", "British • Alan" to "alan",
            "British • Alba" to "alba", "British • Jenny" to "jenny_dioco",
            "Northern British • Male" to "northern_english_male",
            "Jasper • natural male" to "kitten_0", "Bella • natural female" to "kitten_1",
            "Bruno • natural male" to "kitten_2", "Luna • natural female" to "kitten_3",
            "Hugo • natural male" to "kitten_4", "Rosie • natural female" to "kitten_5",
            "Leo • natural male" to "kitten_6", "Kiki • natural female" to "kitten_7",
            "Android system voice" to "android"
        )
        content.addView(text("CHARACTER VOICE", 10f, Color.rgb(232, 70, 78)).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(9), 0, dp(4))
        })
        val voice = Spinner(this)
        voice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames.map { it.first })
        val selectedVoice = voiceNames.indexOfFirst { it.second == (existing?.voiceKey ?: "lessac") }.coerceAtLeast(0)
        voice.setSelection(selectedVoice)
        content.addView(voice, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(action("CHOOSE CHARACTER PORTRAIT") { pickAvatar.launch(arrayOf("image/*")) },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        content.addView(text(if (pendingAvatar.isBlank()) "No portrait selected" else "Portrait selected", 10f))
        scroll.addView(content)
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Create character" else "Edit character")
            .setView(scroll).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cleanName = name.text.toString().trim()
                if (cleanName.isBlank()) { name.error = "Give this character a name first."; return@setOnClickListener }
                val selectedVoiceKey = voiceNames.getOrNull(voice.selectedItemPosition)?.second ?: "lessac"
                val profile = CharacterProfile(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = cleanName, description = description.text.toString().trim(),
                    personality = personality.text.toString().trim(), scenario = scenario.text.toString().trim(),
                    greeting = greeting.text.toString().trim(), exampleDialogue = examples.text.toString().trim(),
                    systemPrompt = instructions.text.toString().trim(), voiceKey = selectedVoiceKey, avatarUri = pendingAvatar
                )
                store.save(profile)
                if (store.activeId() == profile.id) store.setActive(profile)
                render()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}