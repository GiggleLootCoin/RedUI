package com.giggleloot.redui

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small app-private encrypted file store.
 *
 * AES-256-GCM uses a non-exportable Android Keystore key. Each write gets a fresh
 * 96-bit IV. The format header is authenticated so a modified file is rejected.
 * Legacy plaintext files are encrypted in place on their first successful read.
 */
object LocalVault {
    private const val KEY_ALIAS = "redui.local.vault.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val header = byteArrayOf(0x52, 0x44, 0x55, 0x31) // "RDU1"
    private val lock = Any()

    fun writeText(file: File, value: String) = writeBytes(file, value.toByteArray(Charsets.UTF_8))

    fun readText(file: File): String? =
        readBytes(file)?.toString(Charsets.UTF_8)

    @Synchronized
    fun writeBytes(file: File, plaintext: ByteArray) {
        synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(createIfMissing = true)
                ?: throw IllegalStateException("The local encryption key could not be created."))
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plaintext)
            val encoded = ByteArray(header.size + cipher.iv.size + encrypted.size)
            System.arraycopy(header, 0, encoded, 0, header.size)
            System.arraycopy(cipher.iv, 0, encoded, header.size, cipher.iv.size)
            System.arraycopy(encrypted, 0, encoded, header.size + cipher.iv.size, encrypted.size)

            val parent = file.parentFile ?: throw IllegalStateException("Encrypted storage has no parent directory.")
            if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create encrypted storage.")
            val temporary = File(parent, file.name + ".vault-tmp")
            FileOutputStream(temporary).use { stream ->
                stream.write(encoded)
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    @Synchronized
    fun readBytes(file: File): ByteArray? {
        synchronized(lock) {
            if (!file.isFile) return null
            val encoded = file.readBytes()
            if (!hasHeader(encoded)) {
                // One-time migration for data created by older RedUI builds.
                // The file is rewritten as ciphertext before its contents are returned.
                writeBytes(file, encoded)
                return encoded
            }
            if (encoded.size < header.size + IV_BYTES + 16) {
                throw IllegalStateException("Encrypted local data is incomplete and cannot be safely opened.")
            }
            val iv = encoded.copyOfRange(header.size, header.size + IV_BYTES)
            val ciphertext = encoded.copyOfRange(header.size + IV_BYTES, encoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val decryptKey = key(createIfMissing = false)
                ?: throw IllegalStateException("Encrypted local data cannot be opened because its device key is unavailable.")
            cipher.init(Cipher.DECRYPT_MODE, decryptKey, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            return cipher.doFinal(ciphertext)
        }
    }

    private fun hasHeader(value: ByteArray): Boolean =
        value.size >= header.size && header.indices.all { value[it] == header[it] }

    private fun key(createIfMissing: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        if (!createIfMissing) return null

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUnlockedDeviceRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()

    }
}
