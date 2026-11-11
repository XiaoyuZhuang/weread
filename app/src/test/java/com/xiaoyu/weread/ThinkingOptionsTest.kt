package com.xiaoyu.weread

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThinkingOptionsTest {
    @Test fun disabledOmitsEffortAndKeepsOtherFields() {
        val payload = JSONObject().put("model", "deepseek-flash").put("max_tokens", 2048)
        ThinkingOptions.applyTo(payload, true)
        ThinkingOptions.applyTo(payload, false)
        assertEquals("disabled", payload.getJSONObject("thinking").getString("type"))
        assertFalse(payload.has("output_config"))
        assertEquals(2048, payload.getInt("max_tokens"))
        assertEquals("deepseek-flash", payload.getString("model"))
    }

    @Test fun enabledAlwaysUsesLowEffort() {
        val payload = JSONObject()
        ThinkingOptions.applyTo(payload, true)
        assertEquals("enabled", payload.getJSONObject("thinking").getString("type"))
        assertEquals("low", payload.getJSONObject("output_config").getString("effort"))
    }
}
