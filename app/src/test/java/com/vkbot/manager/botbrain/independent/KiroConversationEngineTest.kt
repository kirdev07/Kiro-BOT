package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroConversationEngineTest {
    private val engine = KiroConversationEngine(listOf(
        KiroReplyIndex.Record("welcome", "Добрый день", "Здравствуйте!")
    ))

    @Test fun returnsMatchedAnswerWithRecordId() {
        assertEquals(KiroConversationEngine.Result.Answer("Здравствуйте!", "welcome"),
            engine.respond("добрый день"))
    }

    @Test fun unknownInputReturnsNoMatch() {
        assertTrue(engine.respond("как дела") is KiroConversationEngine.Result.NoMatch)
    }

    @Test fun blankInputReturnsNoMatch() {
        assertTrue(engine.respond("   ") is KiroConversationEngine.Result.NoMatch)
    }
}
