package com.xiaoyu.weread

/** Parses the model's final labeled answer; ignores unrelated web-search citations. */
object AnswerParser {
    private val countPattern = Regex("""(?im)^[ \t]*COUNT[ \t]*[:=：][ \t]*([234])[ \t]*$""")
    private val answerPattern = Regex("""(?im)^[ \t]*ANSWER[ \t]*[:=：][ \t]*([1-4])[ \t]*$""")

    /** Returns (one-based correct index, number of actual choices). */
    fun parse(content: String): Pair<Int, Int>? {
        val count = countPattern.findAll(content).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val choice = answerPattern.findAll(content).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return if (choice in 1..count) choice to count else null
    }
}
