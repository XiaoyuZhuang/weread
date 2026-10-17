package com.xiaoyu.weread

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.math.max
import kotlin.math.min

/** Chinese OCR with visual detection of an underline missing from the text. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    fun extract(bitmap: Bitmap, callback: (Result<String>) -> Unit) {
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE },
                        { it.boundingBox?.left ?: Int.MAX_VALUE }))
                    .map { line -> preserveUnderline(bitmap, line) }
                    .filter { it.isNotBlank() }
                bitmap.recycle()
                if (lines.isEmpty()) callback(Result.failure(IllegalStateException("未识别到文字")))
                else callback(Result.success(lines.joinToString("\n")))
            }
            .addOnFailureListener { error ->
                bitmap.recycle()
                callback(Result.failure(error))
            }
    }

    private fun preserveUnderline(bitmap: Bitmap, line: Text.Line): String {
        val original = line.text.trim()
        val box = line.boundingBox ?: return original
        if (original.length !in 2..36 ||
            !original.any { it in '\u4e00'..'\u9fff' }) return original
        val centerX = findLongHorizontalStroke(bitmap, box) ?: return original
        val parts = line.elements.mapNotNull { element ->
            element.boundingBox?.let { OcrTextPart(element.text, it.left, it.right) }
        }
        return QuestionBlankPlacement.insert(original, centerX, parts, box.left, box.right)
    }

    /** Detect a white horizontal stroke significantly longer than one glyph. */
    private fun findLongHorizontalStroke(bitmap: Bitmap, box: Rect): Int? {
        val height = box.height()
        if (height < 18 || box.width() < height * 2) return null
        val left = max(0, box.left - height / 2)
        val right = min(bitmap.width, box.right + height / 2)
        val top = max(0, box.top - height / 4)
        val bottom = min(bitmap.height, box.bottom + height / 4)
        val width = right - left
        val rows = bottom - top
        if (width <= 0 || rows <= 0) return null
        val pixels = IntArray(width * rows)
        bitmap.getPixels(pixels, 0, width, left, top, width, rows)
        val minimum = max(38, (height * 1.45).toInt())
        var bestWidth = 0
        var bestX = -1
        for (y in 0 until rows) {
            var runStart = -1
            for (x in 0..width) {
                val bright = if (x < width) {
                    val p = pixels[y * width + x]
                    Color.red(p) > 185 && Color.green(p) > 185 && Color.blue(p) > 185
                } else false
                if (bright && runStart < 0) runStart = x
                if (!bright && runStart >= 0) {
                    val run = x - runStart
                    if (run >= minimum && run > bestWidth) {
                        bestWidth = run
                        bestX = runStart
                    }
                    runStart = -1
                }
            }
        }
        return if (bestX >= 0) left + bestX + bestWidth / 2 else null
    }

    fun close() = recognizer.close()
}
