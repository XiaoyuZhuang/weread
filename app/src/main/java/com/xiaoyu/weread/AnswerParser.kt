package com.xiaoyu.weread

/** Pure Kotlin parser so the model's free-form search citations cannot be mistaken for answers. */
object AnswerParser {
    private val countPattern = Regex("""(?im)^\\s*COUNT\\s*[:=：]\\s*([234])\\s*$""")
    private val answerPattern = Regex("""(?im)^\\s*ANSWER\\s*[:=：]\\s*([1-4])\\s*$""")

    /** Returns (one-based correct index, number of actual choices). */
    fun parse(content: String): Pair<Int, Int>? {
        val count = countPattern.findAll(content).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val choice = answerPattern.findAll(content).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return if (choice in 1..count) choice to count else null
    }
}
