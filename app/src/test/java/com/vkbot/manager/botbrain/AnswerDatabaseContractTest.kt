package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search behavior that must remain stable during the database rewrite. */
class AnswerDatabaseContractTest {
    @Test fun blankQueriesHaveNoResults() {
        val database = AnswerDatabase(listOf(AnswerElement(1, "привет", "здравствуй")))
        assertTrue(database.searchAnswers(null, null).isEmpty())
        assertTrue(database.searchAnswers("  ", "").isEmpty())
    }

    @Test fun exactMatchesOutrankOtherCandidates() {
        val database = AnswerDatabase(listOf(
            AnswerElement(1, "привет", "здравствуй"),
            AnswerElement(2, "привет друг", "рад видеть")
        ))
        val results = database.searchAnswers("Привет!!!", "")
        assertTrue(results.isNotEmpty())
        assertEquals(1L, results.first().answer.id)
        assertEquals(1, results.first().tier)
    }

    @Test fun matchingContextIsPreferredToGeneralAnswer() {
        val database = AnswerDatabase(listOf(
            AnswerElement(1, "привет", "общий"),
            AnswerElement(2, "привет", "контекстный", requiredContext = "friends")
        ))
        val results = database.searchAnswers("привет", "FRIENDS")
        assertEquals(2L, results.first().answer.id)
        assertEquals(0, results.first().tier)
        assertTrue(results.any { it.answer.id == 1L && it.tier == 1 })
    }

    @Test fun mismatchedContextIsExcluded() {
        val database = AnswerDatabase(listOf(
            AnswerElement(1, "привет", "только друзья", requiredContext = "friends")
        ))
        assertFalse(database.searchAnswers("привет", "strangers").any { it.answer.id == 1L })
    }

    @Test fun regexCapturesGroups() {
        val database = AnswerDatabase(listOf(AnswerElement(1, "regex:hello (\\w+)", "hi")))
        val result = database.searchAnswers("hello Alex", "").first { it.answer.id == 1L }
        assertEquals(3, result.tier)
        assertEquals(listOf("hello Alex", "Alex"), result.capturedGroups)
    }

    @Test fun usageCountDoesNotChangeOnSearch() {
        val database = AnswerDatabase(listOf(AnswerElement(1, "привет", "здравствуй")))
        val result = database.searchAnswers("привет", "").first()
        assertEquals(0, result.answer.usageCount)
        database.recordUsage(result.answer)
        assertEquals(1, result.answer.usageCount)
    }
}
