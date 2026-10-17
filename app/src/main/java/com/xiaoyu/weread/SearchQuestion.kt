package com.xiaoyu.weread

/** Converts the vision model's structured question into an official search query. */
object SearchQuestion {
    private val questionLine = Regex("""(?im)^\s*QUESTION\s*[:：]\s*(.+?)\s*$""")
    private val optionLine = Regex("""(?im)^\s*OPTION[1-4]\s*[:：]\s*(.+?)\s*$""")
    private val numberedOption = Regex("""(?im)^\s*OPTION([1-4])\s*[:：]""")

    fun fromVision(text: String): String {
        val question = questionLine.find(text)?.groupValues?.get(1)?.trim()
            ?: throw IllegalStateException("图片中未识别到题干")
        val options = optionLine.findAll(text).map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }.take(4).toList()
        if (options.size < 2) throw IllegalStateException("未识别到至少两个选项")
        // Official Search API accepts 1..128 Unicode characters. Treat OCR as data,
        // strip control characters, and retain short alternatives for fill-in questions.
        val normalized = question.replace("[填空]", " ").replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val extras = if (question.contains("[填空]")) options.joinToString(" ") { it.take(14) } else ""
        return (normalized + " " + extras).trim().take(128)
            .ifBlank { throw IllegalStateException("题干为空，无法搜索") }
    }

    fun optionCount(text: String): Int =
        numberedOption.findAll(text).map { it.groupValues[1].toInt() }.distinct().count()
}
