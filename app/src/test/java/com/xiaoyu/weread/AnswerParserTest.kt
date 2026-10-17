package com.xiaoyu.weread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerParserTest {
    @Test fun parsesTwoOptions() {
        assertEquals(1 to 2, AnswerParser.parse("COUNT=2\nANSWER=1"))
    }

    @Test fun parsesFourOptions() {
        assertEquals(4 to 4, AnswerParser.parse("COUNT=4\nANSWER=4"))
    }

    @Test fun rejectsOutOfRangeAnswer() {
        assertNull(AnswerParser.parse("COUNT=2\nANSWER=4"))
    }

    @Test fun rejectsUnlabeledSearchSnippets() {
        assertNull(AnswerParser.parse("第1题搜索结果：2个答案。"))
    }

    @Test fun prefersLastLabeledAnswer() {
        assertEquals(3 to 4, AnswerParser.parse("COUNT=2\nANSWER=1\nCOUNT=4\nANSWER=3"))
    }
}
