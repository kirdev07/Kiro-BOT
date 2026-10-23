package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroReplyIndexTest {
    private val index = KiroReplyIndex(listOf(
        KiroReplyIndex.Record("hello", "Привет", "Привет!"),
        KiroReplyIndex.Record("weather", "Какая погода", "Проверь прогноз")
    ))

    @Test fun exactMatchIgnoresCaseAndPunctuation() {
        assertEquals("Привет!", index.find("ПРИВЕТ!!!")?.reply)
    }

    @Test fun partialPhraseMatchesWithSufficientOverlap() {
        assertEquals("Проверь прогноз", index.find("какая погода сегодня")?.reply)
    }

    @Test fun unrelatedMessageDoesNotMatch() {
        assertNull(index.find("что нового"))
    }

    @Test fun emptyCorpusReturnsNoReply() {
        assertNull(KiroReplyIndex(emptyList()).find("привет"))
    }

    @Test fun duplicateExactPromptUsesFirstRecord() {
        val repeated = KiroReplyIndex(listOf(
            KiroReplyIndex.Record("first", "тест", "один"),
            KiroReplyIndex.Record("second", "тест", "два")
        ))
        assertEquals("один", repeated.find("тест")?.reply)
    }
}
