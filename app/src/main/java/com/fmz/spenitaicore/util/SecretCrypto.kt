package com.fmz.spenitaicore.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (e.g. the remote AI API key) at rest using an
 * AES-GCM key stored in the Android Keystore. The key never leaves the
 * secure hardware-backed storage, so the plaintext secret cannot be read
 * by simply copying the app's data files.
 */
object SecretCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "spenit_ai_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns base64(iv || ciphertext), or null if encryption is unavailable. */
    fun encrypt(plainText: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /** Decrypts a value produced by [encrypt]; returns null on any failure. */
    fun decrypt(payloadB64: String): String? {
        return try {
            val payload = Base64.decode(payloadB64, Base64.NO_WRAP)
            if (payload.size <= IV_SIZE_BYTES) return null
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
