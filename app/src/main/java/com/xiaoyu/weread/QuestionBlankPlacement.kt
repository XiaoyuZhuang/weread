package com.xiaoyu.weread

import kotlin.math.roundToInt

data class OcrTextPart(val text: String, val left: Int, val right: Int)

/** Place the image-detected gap into the linear OCR text stream. */
object QuestionBlankPlacement {
    fun insert(text: String, blankX: Int, parts: List<OcrTextPart>, left: Int, right: Int): String {
        if (text.contains("[填空]") || text.length < 2) return text
        var cursor = 0
        for (part in parts.sortedBy { it.left }) {
            if (part.text.isEmpty()) continue
            val at = text.indexOf(part.text, cursor)
            if (at < 0) continue
            if (blankX < part.left && at in 1 until text.length) {
                return text.substring(0, at) + "[填空]" + text.substring(at)
            }
            cursor = at + part.text.length
        }
        val span = (right - left).coerceAtLeast(1)
        val estimatedIndex = ((blankX - left).toDouble() / span * text.length).roundToInt()
            .coerceIn(1, text.length - 1)
        return text.substring(0, estimatedIndex) + "[填空]" + text.substring(estimatedIndex)
    }
}
