package com.vkbot.manager.botbrain.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogHistoryTest {

    private var now = 1_000_000L
    private val history = DialogHistory(maxDialogs = 3) { now }

    @Test fun keepsPairsWithTimeAndLastMessages() {
        val d = history.dialog("", "1")
        d.addTurn("как дела", "норм", "база")
        now += 1000
        d.addTurn("а ты?", "тоже", "продолжение")
        assertEquals("а ты?", d.lastUserMessage())
        assertEquals("тоже", d.lastBotResponse())
        assertEquals(listOf("как дела", "а ты?"), d.recentTurns(5).map { it.userText })
        assertEquals(1_001_000L, d.lastTurn()!!.time)
    }

    @Test fun storesAtMostTwentyTurns() {
        val d = history.dialog("", "1")
        repeat(30) { d.addTurn("q$it", "a$it", "база") }
        assertEquals(DialogHistory.MAX_TURNS, d.recentTurns(100).size)
        assertEquals("q29", d.lastUserMessage())
    }

    @Test fun oldTurnsAreNotUsedForFollowUp() {
        val d = history.dialog("", "1")
        d.addTurn("как дела", "норм", "база")
        now += DialogHistory.FOLLOW_UP_TTL + 1
        assertNull(d.lastTurn(DialogHistory.FOLLOW_UP_TTL))
        now += DialogHistory.HISTORY_TTL
        assertTrue(d.recentTurns().isEmpty())
    }

    @Test fun usersAndChatsAreSeparated() {
        val inChatA = history.dialog("chatA", "1")
        assertSame(inChatA, history.dialog("chatA", "1"))
        assertNotSame(inChatA, history.dialog("chatB", "1"))
        assertNotSame(inChatA, history.dialog("chatA", "2"))
        // Личка: chatId пустой или равен userId — один и тот же диалог
        assertSame(history.dialog("", "5"), history.dialog("5", "5"))
    }

    @Test fun leastRecentlyUsedDialogsAreDropped() {
        val first = history.dialog("", "1")
        history.dialog("", "2"); history.dialog("", "3"); history.dialog("", "4")
        assertEquals(3, history.size)
        assertNotSame(first, history.dialog("", "1"))
    }

    @Test fun contextExpires() {
        val d = history.dialog("", "1")
        d.setContext("дела")
        assertEquals("дела", d.context())
        now += DialogHistory.CONTEXT_TTL + 1
        assertEquals("", d.context())
    }

    @Test fun cooldownCoversLastTurnsAndMinutes() {
        val d = history.dialog("", "1")
        d.addTurn("привет", "Привет!", "база")
        assertTrue(d.recentlySaid("Привет!"))
        repeat(DialogHistory.COOLDOWN_TURNS) { d.addTurn("x$it", "y$it", "база") }
        assertTrue("ещё не прошло 10 минут", d.recentlySaid("Привет!"))
        now += DialogHistory.COOLDOWN_MS + 1
        assertFalse(d.recentlySaid("Привет!"))
        assertEquals(DialogHistory.COOLDOWN_TURNS, d.turnsSinceSaid("Привет!"))
    }

    @Test fun punctuationAndCaseDoNotHideRepeat() {
        val d = history.dialog("", "1")
        d.addTurn("ясно", "Ясненько-понятненько.", "база")
        assertTrue(d.recentlySaid("ясненько-понятненько!"))
        assertFalse(d.recentlySaid("Ясненько"))
    }

    @Test fun factsExpireAndCanBeCleared() {
        val d = history.dialog("", "1")
        d.setFact("name", "Кирилл")
        assertEquals("Кирилл", d.fact("name"))
        d.clearFacts()
        assertNull(d.fact("name"))
        d.setFact("name", "Кир")
        now += DialogHistory.FACT_TTL + 1
        assertNull(d.fact("name"))
    }
}
