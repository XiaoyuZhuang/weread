package com.xiaoyu.weread

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private var context: Context? = null
    fun init(value: Context) { context = value.applicationContext }
    private fun prefs() = context?.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs()?.getBoolean("enabled", false) ?: false
        set(value) { prefs()?.edit()?.putBoolean("enabled", value)?.apply() }
    private fun file() = context?.let { File(it.filesDir, "diagnostics.txt") }
    @Synchronized fun write(stage: String, message: String) {
        if (!enabled) return
        runCatching {
            val key = context?.let { SecureConfig(it).loadKey() }.orEmpty()
            var safe = message
            if (key.isNotBlank()) safe = safe.replace(key, "[密钥已隐藏]")
            safe = safe.replace(Regex("sk-[A-Za-z0-9_-]+"), "[密钥已隐藏]")
            val line = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()) +
                " [" + stage + "] " + safe.take(3000) + "
"
            val f = file() ?: return
            val old = if (f.exists()) f.readText() else ""
            f.writeText((line + old).take(100000))
        }
    }
    @Synchronized fun read(): String = runCatching { file()?.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    @Synchronized fun clear() { file()?.delete() }
}
