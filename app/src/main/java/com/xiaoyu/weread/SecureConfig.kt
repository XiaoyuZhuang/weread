package com.xiaoyu.weread

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The API key never enters source control, logs, Android Auto Backup, or a
 * plain-text SharedPreferences value. Stored ciphertext is encrypted with
 * a device-bound Android Keystore AES-GCM key.
 */
class SecureConfig(context: Context) {
    private val prefs = context.getSharedPreferences("weread_config", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) { prefs.edit().putString("endpoint", value.trim()).apply() }

    var model: String
        get() = prefs.getString("model", "jev-1.13") ?: "jev-1.13"
        set(value) { prefs.edit().putString("model", value.trim()).apply() }

    var optionCount: Int
        get() = prefs.getInt("optionCount", 2).coerceIn(2, 4)
        set(value) { prefs.edit().putInt("optionCount", value.coerceIn(2, 4)).apply() }

    fun saveKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove("key_cipher").remove("key_iv").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadKey(): String {
        val ciphertext = prefs.getString("key_cipher", null) ?: return ""
        val iv = prefs.getString("key_iv", null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            "" // Invalidated keystore entries require entering the key again.
        }
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keystore.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://jev-ai.org/api/v1/systemone/"
        private const val ALIAS = "weread_jev_api_key_v1"
    }
}
