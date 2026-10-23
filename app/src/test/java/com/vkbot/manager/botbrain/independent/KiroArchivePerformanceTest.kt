package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroArchivePerformanceTest {
    @Test fun wildcardRulesKeepOriginalOrderAcrossBuckets() {
        val rows = listOf(
            KiroAnswerArchive.Row(1, "*мир", "первый", "", "", "", "", ""),
            KiroAnswerArchive.Row(2, "добрый*", "второй", "", "", "", "", "")
        )
        assertEquals("первый", KiroArchiveSwitch(rows).reply(1L, "добрый мир"))
    }

    @Test fun fifteenThousandRowsKeepIndependentChatState() {
        val rows = (1..15000).map { i ->
            KiroAnswerArchive.Row(i, "вопрос $i", "ответ $i", "", "", "", "", "")
        }
        val router = KiroArchiveSwitch(rows, maxSessions = 4)
        assertEquals("ответ 15000", router.reply(1L, "вопрос 15000"))
        assertEquals("ответ 1", router.reply(2L, "вопрос 1"))
        assertNull(router.reply(3L, "несуществующий вопрос"))
    }

    @Test fun wildcardBucketsStillMatchLeadingStarAndWhitespace() {
        val rows = listOf(
            KiroAnswerArchive.Row(1, "привет*", "обычный", "", "", "", "", ""),
            KiroAnswerArchive.Row(2, " *мир", "начальный", "", "", "", "", "")
        )
        val router = KiroArchiveSwitch(rows)
        assertEquals("обычный", router.reply(1L, "привет всем"))
        assertEquals("начальный", router.reply(1L, "добрый мир"))
    }
}
