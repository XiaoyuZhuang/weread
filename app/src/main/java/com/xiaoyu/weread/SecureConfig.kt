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
        get() = prefs.getString("groq_endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) { prefs.edit().putString("groq_endpoint", value.trim()).apply() }

    var model: String
        get() = prefs.getString("groq_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) { prefs.edit().putString("groq_model", value.trim()).apply() }

    var browserSearch: Boolean
        get() = prefs.getBoolean("groq_browser_search", true)
        set(value) { prefs.edit().putBoolean("groq_browser_search", value).apply() }

    var temperature: Double
        get() = Double.fromBits(prefs.getLong("groq_temperature", 0.2.toBits())).takeIf { it in 0.0..2.0 } ?: 0.2
        set(value) { prefs.edit().putLong("groq_temperature", value.coerceIn(0.0, 2.0).toBits()).apply() }

    var retryTemperature: Double
        get() = Double.fromBits(prefs.getLong("groq_retry_temperature", 0.1.toBits())).takeIf { it in 0.0..2.0 } ?: 0.1
        set(value) { prefs.edit().putLong("groq_retry_temperature", value.coerceIn(0.0, 2.0).toBits()).apply() }

    var reasoningEffort: String
        get() = prefs.getString("groq_reasoning", "low")?.takeIf { it in REASONING_OPTIONS } ?: "low"
        set(value) { prefs.edit().putString("groq_reasoning", value).apply() }

    var retryReasoningEffort: String
        get() = prefs.getString("groq_retry_reasoning", "medium")?.takeIf { it in REASONING_OPTIONS } ?: "medium"
        set(value) { prefs.edit().putString("groq_retry_reasoning", value).apply() }

    fun tuning() = GroqTuning(temperature, retryTemperature, reasoningEffort, retryReasoningEffort)

    fun saveKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove("groq_key_cipher").remove("groq_key_iv").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("groq_key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("groq_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadKey(): String {
        val ciphertext = prefs.getString("groq_key_cipher", null) ?: return ""
        val iv = prefs.getString("groq_key_iv", null) ?: return ""
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
        const val DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        val REASONING_OPTIONS = listOf("low", "medium", "high")
        private const val ALIAS = "weread_jev_api_key_v1" // Device-bound AES key also protects Groq key
    }
}
