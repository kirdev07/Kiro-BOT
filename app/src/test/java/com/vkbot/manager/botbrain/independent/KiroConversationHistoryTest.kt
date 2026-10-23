package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroConversationHistoryTest {
    private val records = listOf(KiroReplyIndex.Record("greeting", "Привет", "Здравствуйте"))

    @Test fun recordsOnlyMatchedConversations() {
        val engine = KiroConversationEngine(records)
        engine.respond("Привет")
        engine.respond("неизвестное сообщение")
        assertEquals(1, engine.recentTurns().size)
        assertEquals("Здравствуйте", engine.recentTurns().first().botReply)
    }

    @Test fun resetClearsSessionHistory() {
        val engine = KiroConversationEngine(records)
        engine.respond("Привет")
        engine.resetSession()
        assertTrue(engine.recentTurns().isEmpty())
    }

    @Test fun instancesDoNotShareHistory() {
        val first = KiroConversationEngine(records)
        val second = KiroConversationEngine(records)
        first.respond("Привет")
        assertEquals(1, first.recentTurns().size)
        assertTrue(second.recentTurns().isEmpty())
    }
}
