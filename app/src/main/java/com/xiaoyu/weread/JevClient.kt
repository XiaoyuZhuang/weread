package com.xiaoyu.weread

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Answer(val index: Int, val confidence: Double?, val elapsedMs: Long)

/**
 * Jev SystemOne is a typed decision API, NOT OpenAI chat completions.
 * Contract: POST {model,state,questions.answer:{type:"choice",criteria:{...}}}
 * Response: answers.answer.choice, answers.answer.confidence.
 */
class JevClient {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun ask(
        ocrText: String,
        apiKey: String,
        endpoint: String,
        model: String,
        optionCount: Int,
        callback: (Result<Answer>) -> Unit
    ) {
        executor.execute {
            callback(runCatching { request(ocrText, apiKey, endpoint, model, optionCount) })
        }
    }

    private fun request(
        ocrText: String,
        apiKey: String,
        endpoint: String,
        model: String,
        optionCount: Int
    ): Answer {
        val url = URL(endpoint)
        require(url.protocol == "https" && !url.host.isNullOrBlank() &&
            url.userInfo == null) { "API 地址必须是 HTTPS" }

        val criteria = JSONObject()
        for (i in 1..optionCount) {
            criteria.put(i.toString(), "截图里从上到下第${i}个真正的答题选项（不包括题号、等级、按钮）")
        }
        val prompt = """
            你正在选择微信读书「每日一答」的正确答案。
            截图已由中文 OCR 转成文字，按页面从上到下排序。
            识别题干及从上到下共${optionCount}个作答选项；忽略等级、题号、举报等界面元素。
            根据题目的知识与常识判断最可能正确的答案。返回对应选项编号。
        """.trimIndent()
        val body = JSONObject()
            .put("model", model)
            .put("state", "OCR 逐行文本（保留从上到下顺序）：\n${ocrText.take(12000)}")
            .put("questions", JSONObject().put("answer",
                JSONObject()
                    .put("type", "choice")
                    .put("instructions", prompt)
                    .put("criteria", criteria)))

        val started = System.currentTimeMillis()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 10000
            instanceFollowRedirects = false // Do not forward Authorization to another host.
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream
                            else connection.errorStream)?.bufferedReader()?.use { it.readText() }
                ?: ""
            if (status !in 200..299) {
                val detail = runCatching {
                    val obj = JSONObject(response)
                    obj.optJSONObject("error")?.optString("message")
                        ?: obj.optString("detail", obj.optString("message", ""))
                }.getOrDefault("")
                throw IllegalStateException("Jev HTTP $status：${detail.take(100)}")
            }
            val answer = JSONObject(response)
                .getJSONObject("answers")
                .getJSONObject("answer")
            val choice = answer.getString("choice").trim().toIntOrNull()
                ?: throw IllegalStateException("模型没有返回选项编号")
            require(choice in 1..optionCount) { "模型返回了无效选项：$choice" }
            val confidence = if (answer.has("confidence") && !answer.isNull("confidence"))
                answer.optDouble("confidence").takeIf { !it.isNaN() } else null
            return Answer(choice, confidence, System.currentTimeMillis() - started)
        } finally {
            connection.disconnect()
        }
    }

    fun close() = executor.shutdownNow()
}
