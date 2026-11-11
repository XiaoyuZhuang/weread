package com.xiaoyu.weread

import org.json.JSONObject

/** DeepSeek's Anthropic Messages format; never send effort alongside disabled thinking. */
internal object ThinkingOptions {
    fun applyTo(payload: JSONObject, enabled: Boolean) {
        payload.put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
        if (enabled) payload.put("output_config", JSONObject().put("effort", "low"))
        else payload.remove("output_config")
    }
}
