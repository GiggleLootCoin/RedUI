package com.giggleloot.redui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class HordeImageResult(val file: File, val queuePosition: Int, val waitSeconds: Int)

class AiHordeImageService(private val context: Context) {
    private val base = "https://aihorde.net/api/v2"
    private val anonymousKey = "0000000000"

    fun generate(
        prompt: String,
        negative: String,
        width: Int,
        height: Int,
        steps: Int,
        sourceUri: Uri?,
        onStatus: (String) -> Unit
    ): HordeImageResult {
        require(prompt.isNotBlank()) { "Write an image prompt first." }
        val fullPrompt = prompt.trim().take(1800) +
            if (negative.isBlank()) "" else "###" + negative.trim().take(700)
        val params = JSONObject()
            .put("width", width)
            .put("height", height)
            .put("steps", steps.coerceIn(10, 30))
            .put("cfg_scale", 7.0)
            .put("sampler_name", "k_euler")
            .put("karras", true)
            .put("n", 1)
            .put("tiling", false)
        val request = JSONObject()
            .put("prompt", fullPrompt)
            .put("params", params)
            .put("nsfw", false)
            .put("censor_nsfw", true)
            .put("r2", true)
            .put("shared", true)
            .put("replacement_filter", true)

        if (sourceUri != null) {
            request.put("source_image", encodeSourceImage(sourceUri))
            request.put("source_processing", "img2img")
            params.put("denoising_strength", 0.58)
        }

        onStatus("Sending request to free volunteer image workers…")
        val submitted = postJson("$base/generate/async", request)
        val id = submitted.optString("id")
        if (id.isBlank()) throw IllegalStateException("The image service accepted no request ID. " + submitted.optString("message"))
        val queue = submitted.optInt("queue_position", -1)
        val wait = submitted.optInt("wait_time", -1)
        onStatus(if (queue >= 0) "Queued • position $queue" else "Queued • waiting for a volunteer worker")

        var completed = false
        var fault = ""
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L
        var lastStatus = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5000L)
            val check = getJson("$base/generate/check/" + enc(id))
            val waiting = check.optInt("waiting", -1)
            val processing = check.optInt("processing", -1)
            val finished = check.optInt("finished", -1)
            val now = System.currentTimeMillis()
            if (now - lastStatus >= 5000L) {
                onStatus(when {
                    waiting > 0 -> "In volunteer queue • $waiting waiting"
                    processing > 0 -> "Worker is creating your image…"
                    finished > 0 -> "Image ready • retrieving result…"
                    else -> "Waiting for a free worker…"
                })
                lastStatus = now
            }
            completed = check.optBoolean("done", false)
            fault = check.optString("faulted")
            if (completed || fault.equals("true", true)) break
            if (check.has("is_possible") && !check.optBoolean("is_possible", true)) {
                throw IllegalStateException("No current worker can fulfil this request. Try a simpler prompt or another image size.")
            }
        }
        if (!completed) {
            throw IllegalStateException(if (fault.equals("true", true)) "The volunteer worker could not complete the image." else "The free queue is still running after 10 minutes. No image was downloaded; try again later.")
        }

        val status = getJson("$base/generate/status/" + enc(id))
        if (status.optBoolean("faulted", false)) throw IllegalStateException("The image generation failed on the volunteer worker.")
        val generations = status.optJSONArray("generations")
            ?: throw IllegalStateException("The service finished but returned no image data.")
        if (generations.length() == 0) throw IllegalStateException("The service finished without a generated image.")
        val imageUrl = generations.optJSONObject(0)?.optString("img").orEmpty()
        if (!imageUrl.startsWith("https://")) throw IllegalStateException("The service returned an image link that was not a secure HTTPS address.")

        onStatus("Downloading the generated image…")
        val bytes = getBytes(imageUrl, 18_000_000)
        if (bytes.size < 1000) throw IllegalStateException("The downloaded image was unexpectedly small.")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("The result was not a readable image.")
        val png = ByteArrayOutputStream()
        try {
            if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                throw IllegalStateException("The generated image could not be saved as PNG.")
            }
        } finally { decoded.recycle() }
        val folder = File(context.filesDir, "generated_images").apply { mkdirs() }
        val output = File(folder, "RedUI_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(8) + ".png")
        output.writeBytes(png.toByteArray())
        onStatus("Saved privately in your RedUI image gallery.")
        return HordeImageResult(output, queue, wait)
    }

    private fun encodeSourceImage(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the source image.")
        val original = input.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("The selected source file is not a readable image.")
        var bitmap = original
        val maxSide = 768
        if (original.width > maxSide || original.height > maxSide) {
            val scale = minOf(maxSide.toFloat() / original.width, maxSide.toFloat() / original.height)
            bitmap = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1), true)
            if (bitmap !== original) original.recycle()
        }
        val output = ByteArrayOutputStream()
        var quality = 82
        do {
            output.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 10
        } while (output.size() > 950_000 && quality >= 42)
        bitmap.recycle()
        if (output.size() > 1_100_000) throw IllegalStateException("The source image could not be compressed enough for the free service.")
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", anonymousKey)
            setRequestProperty("Client-Agent", "RedUI:162:https://github.com/GiggleLootCoin/RedUI")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText().take(500_000) }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Image service returned HTTP $code: " + runCatching { JSONObject(text).optString("message") }.getOrDefault("Request failed."))
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", anonymousKey)
            setRequestProperty("Client-Agent", "RedUI:162:https://github.com/GiggleLootCoin/RedUI")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Image queue check returned HTTP $code.")
            val text = connection.inputStream.bufferedReader().use { it.readText().take(500_000) }
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun getBytes(link: String, maxBytes: Int): ByteArray {
        val url = URL(link)
        require(url.protocol == "https" && url.userInfo == null) { "Unsafe image download link rejected." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RedUI/162 (+https://github.com/GiggleLootCoin/RedUI)")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Could not download generated image (HTTP $code).")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IllegalStateException("Generated image exceeds the safe download limit.")
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}