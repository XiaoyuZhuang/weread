package com.xiaoyu.weread

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionBlankPlacementTest {
    @Test fun insertsAtDetectedWordGap() {
        val parts = listOf(OcrTextPart("做张", 280, 402), OcrTextPart("智", 550, 600))
        assertEquals("做张[填空]智", QuestionBlankPlacement.insert("做张智", 493, parts, 280, 600))
    }

    @Test fun insertsWhenOcrMergedWords() {
        assertEquals("做张[填空]智", QuestionBlankPlacement.insert("做张智", 493, emptyList(), 280, 600))
    }
}
