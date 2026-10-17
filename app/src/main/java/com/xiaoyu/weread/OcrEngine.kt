package com.xiaoyu.weread

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/** On-device Chinese OCR. Screenshot pixels are never uploaded to the model. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    fun extract(bitmap: Bitmap, callback: (Result<String>) -> Unit) {
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE },
                        { it.boundingBox?.left ?: Int.MAX_VALUE }))
                    .map { it.text.trim() }.filter { it.isNotEmpty() }
                bitmap.recycle()
                if (lines.isEmpty()) callback(Result.failure(IllegalStateException("未识别到文字")))
                else callback(Result.success(lines.joinToString("\n")))
            }
            .addOnFailureListener { error ->
                bitmap.recycle()
                callback(Result.failure(error))
            }
    }

    fun close() = recognizer.close()
}
