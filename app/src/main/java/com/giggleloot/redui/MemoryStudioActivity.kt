package com.giggleloot.redui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.util.UUID

class MemoryStudioActivity : AppCompatActivity() {
    private lateinit var store: MemoryStore
    private lateinit var list: LinearLayout
    private var exporting = ""

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(2_000_000) }
                ?: throw IllegalStateException("Could not read the selected backup.")
        }.onSuccess { raw ->
            runCatching { store.importJson(raw) }
                .onSuccess { Toast.makeText(this, it.toString() + " memory note(s) imported.", Toast.LENGTH_LONG).show(); render() }
                .onFailure { Toast.makeText(this, it.message ?: "Import failed.", Toast.LENGTH_LONG).show() }
        }.onFailure { Toast.makeText(this, "Could not open that backup.", Toast.LENGTH_LONG).show() }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exporting) }
                ?: throw IllegalStateException("Could not open the export destination.")
        }.onSuccess { Toast.makeText(this, "Memory backup exported.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Memory backup export failed.", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MemoryStore(this)
        buildScreen()
        render()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 11f, color: Int = Color.LTGRAY) =
        TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun action(value: String, primary: Boolean = false, handler: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(if (primary) Color.rgb(184, 25, 36) else Color.rgb(41, 39, 45))
            minHeight = dp(43)
            setOnClickListener { handler() }
        }

    private fun buildScreen() {
        window.statusBarColor = Color.rgb(8, 8, 10)
        window.navigationBarColor = Color.rgb(5, 5, 7)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15))
            setBackgroundColor(Color.rgb(8, 8, 11))
        }
        root.addView(label("MEMORY STUDIO", 23f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
        root.addView(label("Only save information deliberately. Notes stay on this phone and can be reviewed, edited, exported or deleted.", 11f).apply {
            setPadding(0, dp(5), 0, dp(10))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(action("＋ NEW NOTE", true) { edit(null) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(action("BACK UP") { exporting = store.exportJson(); exportBackup.launch("RedUI_Memory_Backup.json") },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(5) })
        row.addView(action("IMPORT") { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(5) })
        root.addView(row)
        root.addView(label("YOUR SAVED NOTES", 10f, Color.rgb(232, 65, 75)).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(15), 0, dp(7))
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(action("DELETE ALL") { confirmClear() }, LinearLayout.LayoutParams(0, dp(45), 1f))
        footer.addView(action("DONE", true) { finish() }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(6) })
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun render() {
        list.removeAllViews()
        val notes = store.all()
        if (notes.isEmpty()) {
            list.addView(label("Your memory library is empty.\n\nCreate a note when there is something you want RedAI to recall, such as a preference, a project detail or a creative idea.", 13f))
            return
        }
        notes.forEach { memory ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12))
                setBackgroundColor(Color.rgb(24, 22, 27))
            }
            card.addView(label(memory.title, 15f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
            card.addView(label(memory.category.uppercase() + if (memory.tags.isBlank()) "" else " • " + memory.tags, 9f, Color.rgb(233, 75, 83)).apply {
                setPadding(0, dp(3), 0, dp(6))
            })
            card.addView(label(memory.content.take(350) + if (memory.content.length > 350) "…" else "", 11f))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(action("EDIT") { edit(memory) }, LinearLayout.LayoutParams(0, dp(40), 1f))
            buttons.addView(action("DELETE") { confirmDelete(memory) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(6) })
            card.addView(buttons, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun edit(existing: LocalMemory?) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(7), dp(18), dp(10)) }
        fun field(title: String, value: String, hint: String, multiline: Boolean): EditText {
            body.addView(label(title, 10f, Color.rgb(232, 65, 75)).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) })
            return EditText(this).apply {
                setText(value)
                this.hint = hint
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                textSize = 14f
                setBackgroundColor(Color.rgb(33, 31, 37))
                setPadding(dp(10))
                minLines = if (multiline) 3 else 1
                maxLines = if (multiline) 8 else 1
                isSingleLine = !multiline
            }.also { body.addView(it) }
        }
        val title = field("NOTE TITLE", existing?.title.orEmpty(), "Give this note a clear name", false)
        val categories = listOf("General", "Preference", "Project", "Creative", "Research", "Reference")
        body.addView(label("CATEGORY", 10f, Color.rgb(232, 65, 75)).apply { setPadding(0, dp(8), 0, dp(3)) })
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MemoryStudioActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            setSelection(categories.indexOf(existing?.category).coerceAtLeast(0))
        }
        body.addView(category)
        val tags = field("SEARCH TAGS", existing?.tags.orEmpty(), "e.g. RedAI, music, voice, project", false)
        val note = field("MEMORY NOTE", existing?.content.orEmpty(), "Write only what you deliberately want saved for future context.", true)
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Save a memory" else "Edit memory")
            .setMessage("A saved note may be included in a future prompt when its words match the topic. It is not sent to a cloud memory service.")
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val text = note.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(this, "The memory note is empty. Nothing was saved.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.save(LocalMemory(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title.text.toString().trim().ifBlank { "Untitled note" },
                    content = text,
                    category = categories.getOrElse(category.selectedItemPosition) { "General" },
                    tags = tags.text.toString().trim(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                ))
                render()
            }.show()
    }

    private fun confirmDelete(memory: LocalMemory) {
        AlertDialog.Builder(this).setTitle("Delete this memory?")
            .setMessage("“" + memory.title + "” will be removed from this phone's local memory store.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ -> store.delete(memory.id); render() }.show()
    }

    private fun confirmClear() {
        if (store.all().isEmpty()) return
        AlertDialog.Builder(this).setTitle("Delete every saved memory?")
            .setMessage("This cannot be undone. Export a backup first if you want to keep any notes.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE ALL") { _, _ -> store.clear(); render() }.show()
    }
}