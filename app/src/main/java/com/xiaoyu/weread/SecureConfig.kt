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

/** All DeepSeek credentials stay encrypted locally and outside Android backup. */
class SecureConfig(context: Context) {
    private val prefs = context.getSharedPreferences("weread_config", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString("deepseek_endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) { prefs.edit().putString("deepseek_endpoint", value.trim().trimEnd('/')).apply() }

    var visionModel: String
        get() = prefs.getString("deepseek_vision_model", DEFAULT_VISION_MODEL) ?: DEFAULT_VISION_MODEL
        set(value) { prefs.edit().putString("deepseek_vision_model", value.trim()).apply() }

    var searchCount: Int
        get() = prefs.getInt("deepseek_search_count", 5).coerceIn(3, 8)
        set(value) { prefs.edit().putInt("deepseek_search_count", value.coerceIn(3, 8)).apply() }

    var themeMode: String
        get() = prefs.getString("appearance", "system")?.takeIf { it in THEME_OPTIONS } ?: "system"
        set(value) { prefs.edit().putString("appearance", value).apply() }

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) { prefs.edit().putBoolean("haptic_enabled", value).apply() }

    var showImageDebug: Boolean
        get() = prefs.getBoolean("show_image_debug", false)
        set(value) { prefs.edit().putBoolean("show_image_debug", value).apply() }

    var temperature: Double
        get() = Double.fromBits(prefs.getLong("deepseek_temperature", 0.2.toBits())).takeIf { it in 0.0..2.0 } ?: 0.2
        set(value) { prefs.edit().putLong("deepseek_temperature", value.coerceIn(0.0, 2.0).toBits()).apply() }


    fun saveKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove("deepseek_key_cipher").remove("deepseek_key_iv").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("deepseek_key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("deepseek_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadKey(): String {
        val ciphertext = prefs.getString("deepseek_key_cipher", null) ?: return ""
        val iv = prefs.getString("deepseek_key_iv", null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com"
        const val DEFAULT_VISION_MODEL = "deepseek-flash"
        val THEME_OPTIONS = listOf("system", "light", "dark")
        private const val ALIAS = "weread_deepseek_api_key_v1"
    }
}
