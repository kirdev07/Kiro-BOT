package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroArchiveConversationTest {
    @Test fun contextGatesFollowUpAndTransitions() {
        val rows = KiroAnswerArchive.importLegacy(
            "привет|здравствуй|||||greeted\nкак дела|отлично||||greeted|"
        )
        val engine = KiroArchiveConversation(rows)
        assertNull(engine.respond("как дела"))
        assertEquals("здравствуй", engine.respond("привет"))
        assertEquals("greeted", engine.currentContext())
        assertEquals("отлично", engine.respond("как дела"))
    }

    @Test fun limitsConsecutiveRepeats() {
        val engine = KiroArchiveConversation(KiroAnswerArchive.importLegacy("привет|здравствуй||2"))
        assertEquals("здравствуй", engine.respond("привет"))
        assertEquals("здравствуй", engine.respond("привет"))
        assertNull(engine.respond("привет"))
    }

    @Test fun resetsConversationState() {
        val engine = KiroArchiveConversation(KiroAnswerArchive.importLegacy("привет|ответ||1"))
        engine.respond("привет")
        assertEquals(1, engine.usageForSourceLine(1))
        engine.reset()
        assertEquals(0, engine.usageForSourceLine(1))
        assertEquals("ответ", engine.respond("привет"))
    }

    @Test fun doesNotExecuteAttachmentsOrPatterns() {
        val engine = KiroArchiveConversation(KiroAnswerArchive.importLegacy(
            "regex:.*|unsafe\nпривет|attachment|photo\nпривет|safe"
        ))
        assertEquals("safe", engine.respond("привет"))
    }
}
