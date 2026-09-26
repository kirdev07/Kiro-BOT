package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnansweredLogTest {

    @Test
    fun sameMeaningIsCountedTogether() {
        val log = UnansweredLog()
        log.record("Как дела?", now = 1)
        log.record("как  дела", now = 2)
        log.record("Сколько будет два плюс два", now = 3)
        val top = log.top()
        assertEquals(2, top.size)
        assertEquals(2, top[0].count)
        assertEquals("как  дела", top[0].text)
        assertTrue(log.isDirty)
    }

    @Test
    fun commandsEmptyAndLongMessagesAreSkipped() {
        val log = UnansweredLog()
        log.record("!запомни a = b")
        log.record("/start")
        log.record("   ")
        log.record("?!…")
        log.record("а".repeat(301))
        assertEquals(0, log.size)
    }

    @Test
    fun rarestEntryIsEvictedWhenFull() {
        val log = UnansweredLog(maxEntries = 2)
        log.record("часто", now = 1); log.record("часто", now = 2)
        log.record("редко", now = 3)
        log.record("новое", now = 4)
        assertEquals(setOf("часто", "новое"), log.top().map { it.text }.toSet())
    }

    @Test
    fun pruneRemovesAnsweredQuestions() {
        val log = UnansweredLog()
        log.record("погода")
        log.record("курс доллара")
        assertEquals(1, log.prune { it == "погода" })
        assertEquals(listOf("курс доллара"), log.top().map { it.text })
    }

    @Test
    fun removeAndJsonRoundTrip() {
        val log = UnansweredLog()
        log.record("Привет, бот!", now = 10); log.record("привет бот", now = 20)
        log.record("мем", now = 5)
        val restored = UnansweredLog.fromJson(log.toJson())
        assertEquals(log.top(), restored.top())
        assertTrue(restored.remove("ПРИВЕТ БОТ"))
        assertFalse(restored.remove("чего нет"))
        assertEquals(listOf("мем"), restored.top().map { it.text })
    }

    @Test
    fun brokenJsonGivesEmptyLog() = assertEquals(0, UnansweredLog.fromJson("not json").size)
}
