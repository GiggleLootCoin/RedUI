package com.giggleloot.redui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Html
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class AttachmentReader(private val context: Context) {
    fun readImage(uri: Uri, name: String): String {
        val image = InputImage.fromFilePath(context, uri)
        val textClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val ocr = try { Tasks.await(textClient.process(image)).text.trim() } finally { textClient.close() }
        val labels = runCatching {
            val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            try { Tasks.await(client.process(image)).sortedByDescending { it.confidence }.take(8) }
            finally { client.close() }
        }.getOrDefault(emptyList())
        return buildString {
            append("[IMAGE: ").append(name).append("]\n")
            if (ocr.isNotBlank()) append("Visible text detected by on-device OCR:\n").append(ocr.take(7000)).append("\n")
            else append("OCR: no readable text detected.\n")
            if (labels.isNotEmpty()) {
                append("On-device image-category suggestions (not a full scene interpretation): ")
                append(labels.joinToString { label -> label.text + " (" + String.format(java.util.Locale.US, "%.0f", label.confidence * 100) + "%)" })
                append(".\n")
            }
            append("Capability boundary: this device-side reader can extract visible text and suggest common image categories. It is not a vision-language model, so do not claim to recognise fine details, relationships, expressions, intent, or a complete scene from these results.")
        }
    }

    fun readDocument(uri: Uri, name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime.equals("application/pdf", true) -> readPdf(uri, name)
            ext == "docx" || mime.contains("wordprocessingml", true) -> readDocx(uri, name)
            ext in setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml", "toml", "kt", "py", "java", "js", "ts", "css", "sql") ||
                mime.startsWith("text/") -> readPlainText(uri, name)
            else -> "[FILE: $name]\nThis file type is not supported for local text extraction ($mime). It has not been treated as analysed."
        }
    }

    private fun readPlainText(uri: Uri, name: String): String {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < 1_500_000) {
                val count = input.read(buffer, 0, minOf(buffer.size, 1_500_000 - total))
                if (count <= 0) break
                output.write(buffer, 0, count)
                total += count
            }
            String(output.toByteArray(), Charsets.UTF_8)
        } ?: throw IllegalStateException("Could not open the selected document.")
        return "[FILE: $name]\n" + text.take(14000) + if (text.length > 14000) "\n[Text truncated for the local context window.]" else ""
    }

    private fun readDocx(uri: Uri, name: String): String {
        val xml = context.contentResolver.openInputStream(uri)?.use { source ->
            ZipInputStream(source).use { zip ->
                var documentXml = ""
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (total < 1_500_000) {
                            val count = zip.read(buffer, 0, minOf(buffer.size, 1_500_000 - total))
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            total += count
                        }
                        documentXml = String(output.toByteArray(), Charsets.UTF_8)
                        break
                    }
                    zip.closeEntry()
                }
                documentXml
            }
        }.orEmpty()
        if (xml.isBlank()) throw IllegalStateException("The DOCX file did not contain readable Word document text.")
        val paragraphs = StringBuilder()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                val value = parser.nextText()
                if (value.isNotBlank()) paragraphs.append(value)
            } else if (event == XmlPullParser.END_TAG && parser.name == "p") {
                paragraphs.append("\n")
            }
            event = parser.next()
        }
        val result = Html.fromHtml(paragraphs.toString(), Html.FROM_HTML_MODE_LEGACY).toString()
        return "[DOCX: $name]\n" + result.take(14000) + if (result.length > 14000) "\n[Text truncated for the local context window.]" else ""
    }

    private fun readPdf(uri: Uri, name: String): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not open the PDF file.")
        val blocks = mutableListOf<String>()
        var total = 0
        try {
            PdfRenderer(descriptor).use { renderer ->
                val pageLimit = minOf(renderer.pageCount, 8)
                for (index in 0 until pageLimit) {
                    val page = renderer.openPage(index)
                    try {
                        val scale = minOf(1.25f, 1100f / page.width.toFloat())
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceIn(1, 1500)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val image = InputImage.fromBitmap(bitmap, 0)
                            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val text = try { Tasks.await(client.process(image)).text.trim() } finally { client.close() }
                            if (text.isNotBlank()) {
                                val part = "PAGE " + (index + 1) + ":\n" + text
                                blocks.add(part)
                                total += part.length
                            } else {
                                blocks.add("PAGE " + (index + 1) + ": no readable text detected by OCR.")
                            }
                        } finally { bitmap.recycle() }
                    } finally { page.close() }
                    if (total >= 10000) break
                }
                if (renderer.pageCount > pageLimit) blocks.add("[Only the first " + pageLimit + " of " + renderer.pageCount + " PDF pages were processed to protect device memory.]")
            }
        } finally {
            descriptor.close()
        }
        return "[PDF: " + name + " — page images processed with on-device OCR]\n" +
            (blocks.joinToString("\n\n").take(13000).ifBlank { "No readable text was extracted. The PDF may be image-only, encrypted, or difficult to read." })
    }
}