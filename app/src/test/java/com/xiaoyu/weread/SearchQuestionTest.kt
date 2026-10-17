package com.xiaoyu.weread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQuestionTest {
    @Test fun extractsQuestion() {
        assertEquals("中国的首都是哪里？", SearchQuestion.fromVision(
            "QUESTION: 中国的首都是哪里？\nOPTION1: 北京\nOPTION2: 上海"
        ))
        assertEquals(2, SearchQuestion.optionCount(
            "QUESTION: 中国的首都是哪里？\nOPTION1: 北京\nOPTION2: 上海"
        ))
    }

    @Test fun includesOptionsForFillIn() {
        val query = SearchQuestion.fromVision(
            "QUESTION: 做张[填空]智\nOPTION1: 知\nOPTION2: 警"
        )
        assertTrue(query.contains("做张"))
        assertTrue(query.contains("知 警"))
    }

    @Test fun enforcesSearchQueryLength() {
        assertEquals(128, SearchQuestion.fromVision(
            "QUESTION: " + "中".repeat(160) + "\nOPTION1: A\nOPTION2: B"
        ).length)
    }
}
