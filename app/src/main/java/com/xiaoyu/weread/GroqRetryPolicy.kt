package com.xiaoyu.weread

/**
 * Only the explicit "required tool wasn't called" HTTP 400 is safe to retry.
 * Other 400s (invalid parameters), 401/403 (credentials), 429 (quota) are not
 * automatically repeated.
 */
object GroqRetryPolicy {
    fun isMissingRequiredTool(status: Int, detail: String): Boolean {
        if (status != 400) return false
        val text = detail.lowercase()
        if (!text.contains("tool")) return false
        return text.contains("didn't call") ||
            text.contains("did not call") ||
            text.contains("not call a tool") ||
            text.contains("failed to call") ||
            text.contains("no tool calls") ||
            text.contains("no tool call")
    }
}
