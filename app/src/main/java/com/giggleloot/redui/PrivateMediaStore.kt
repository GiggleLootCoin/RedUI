package com.giggleloot.redui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Imports user-selected images into app-private encrypted storage.
 * A vault reference contains only a local filename, never a provider URI.
 */
object PrivateMediaStore {
    private const val PREFIX = "vault:"
    private const val MAX_INPUT_BYTES = 20 * 1024 * 1024

    private fun directory(context: Context) =
        File(context.applicationContext.filesDir, "private_media").apply { mkdirs() }

    fun importUri(context: Context, uri: Uri, key: String, maxSide: Int = 1024): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the selected image.")
        val originalBytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_INPUT_BYTES) {
                    throw IllegalArgumentException("The selected image is larger than the 20 MB import limit.")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return importBytes(context, originalBytes, key, maxSide)
    }

    fun importBytes(context: Context, bytes: ByteArray, key: String, maxSide: Int = 1024): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_INPUT_BYTES) { "The selected image is empty or too large." }
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("The selected file is not a readable image.")
        val bitmap = if (original.width > maxSide || original.height > maxSide) {
            val scale = minOf(maxSide.toFloat() / original.width, maxSide.toFloat() / original.height)
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== original) original.recycle() }
        } else original

        val png = bitmap.hasAlpha()
        val output = ByteArrayOutputStream()
        try {
            val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (png) 100 else 88
            if (!bitmap.compress(format, quality, output)) {
                throw IllegalStateException("The selected image could not be encoded.")
            }
        } finally {
            bitmap.recycle()
        }

        val safeKey = key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)
            .ifBlank { UUID.randomUUID().toString() }
        val extension = if (png) "png" else "jpg"
        val filename = "$safeKey.$extension.enc"
        LocalVault.writeBytes(File(directory(context), filename), output.toByteArray())
        return PREFIX + filename
    }

    fun writeEncryptedBytes(context: Context, key: String, extension: String, bytes: ByteArray): String {
        require(extension in setOf("png", "jpg", "webp")) { "Unsupported private image type." }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("The image data is not valid.")
        bitmap.recycle()
        val safeKey = key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)
            .ifBlank { UUID.randomUUID().toString() }
        val filename = "$safeKey.$extension.enc"
        LocalVault.writeBytes(File(directory(context), filename), bytes)
        return PREFIX + filename
    }

    fun readBytes(context: Context, reference: String): ByteArray? {
        if (!reference.startsWith(PREFIX)) return null
        val filename = reference.removePrefix(PREFIX)
        if (filename.isBlank() || filename.contains('/') || filename.contains('\\') ||
            !filename.matches(Regex("[A-Za-z0-9._-]{1,120}\\.enc"))) return null
        val root = directory(context).canonicalFile
        val file = File(root, filename).canonicalFile
        if (!file.toPath().startsWith(root.toPath())) return null
        return LocalVault.readBytes(file)
    }

    fun loadBitmap(context: Context, reference: String): Bitmap? {
        val bytes = readBytes(context, reference) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun delete(context: Context, reference: String) {
        if (!reference.startsWith(PREFIX)) return
        val filename = reference.removePrefix(PREFIX)
        if (!filename.matches(Regex("[A-Za-z0-9._-]{1,120}\\.enc"))) return
        val root = directory(context).canonicalFile
        val file = File(root, filename).canonicalFile
        if (file.toPath().startsWith(root.toPath())) file.delete()
    }
}
