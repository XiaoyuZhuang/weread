package com.xiaoyu.weread

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Answer(val index: Int, val optionCount: Int, val elapsedMs: Long)

/**
 * Groq OpenAI-compatible chat completions API with server-side browser_search.
 * One HTTPS request performs search + reasoning on Groq's servers. Screen image
 * is never sent; only local OCR text is included in the prompt.
 */
class GroqClient {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun ask(
        ocrText: String,
        apiKey: String,
        endpoint: String,
        model: String,
        browserSearch: Boolean,
        callback: (Result<Answer>) -> Unit
    ) {
        executor.execute {
            callback(runCatching { request(ocrText, apiKey, endpoint, model, browserSearch) })
        }
    }

    private fun request(
        ocrText: String,
        apiKey: String,
        endpoint: String,
        model: String,
        browserSearch: Boolean
    ): Answer {
        val url = URL(endpoint)
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null) {
            "API 地址必须是 HTTPS"
        }
        require(apiKey.isNotBlank()) { "请填写 Groq API Key" }

        val instructions = """
            你是微信读书「每日一答」知识问答助手，必须根据题干和选项判断正确答案。
            文字来自手机屏幕的 OCR，按大致阅读顺序排列；可能混有时间、等级、举报等界面文字。
            首先识别题干及实际选项数量（通常为2或4，少数为3），不能将题号/按钮视为选项。
            联网搜索时应依据可靠资料核对事实，避免把搜索页的广告或无关内容当作题目选项。
            不要只根据序号猜测。答案序号严格按照手机画面从上到下的选项位置，从1开始。
            最终回复只允许下面两行，不能输出解释、Markdown 或代码块：
            COUNT=2
            ANSWER=1
            上述数字只是格式示例。COUNT必须是实际的选项数量（2/3/4），无法判别时使用4。
            ANSWER必须是1到COUNT之间正确答案的位置。
        """.trimIndent()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", instructions))
            .put(JSONObject().put("role", "user")
                .put("content", "这是当前微信读书题目的 OCR 内容：\n${ocrText.take(12000)}"))
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
            .put("max_completion_tokens", 1800)
        // GPT-OSS supports low reasoning effort, reducing latency during browsing.
        if (model.startsWith("openai/gpt-oss-")) {
            payload.put("reasoning_effort", "low")
        }
        if (browserSearch) {
            payload.put("tools", JSONArray().put(JSONObject().put("type", "browser_search")))
            payload.put("tool_choice", "required")
        }

        val startedAt = System.currentTimeMillis()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 45000 // Search can take longer than ordinary model inference.
            instanceFollowRedirects = false // Never redirect bearer tokens to a new host.
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream
                else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) {
                val message = runCatching {
                    val error = JSONObject(response).optJSONObject("error")
                    error?.optString("message", "")?.takeIf { it.isNotBlank() }
                        ?: JSONObject(response).optString("message", "")
                }.getOrDefault("")
                throw IllegalStateException("Groq HTTP $status：${message.take(135)}")
            }
            val content = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            val parsed = AnswerParser.parse(content)
                ?: throw IllegalStateException("无法解析模型答案，请重试（模型未返回 COUNT/ANSWER）")
            return Answer(parsed.first, parsed.second, System.currentTimeMillis() - startedAt)
        } finally {
            connection.disconnect()
        }
    }

    fun close() = executor.shutdownNow()
}
