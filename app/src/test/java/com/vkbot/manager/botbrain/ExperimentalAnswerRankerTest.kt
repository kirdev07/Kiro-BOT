package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalAnswerRankerTest {
    @Test fun exactAnswerRanksBeforePartialAnswer() {
        val answers = listOf(
            AnswerElement(1, "посоветуй фильм", "комедия"),
            AnswerElement(2, "посоветуй музыку", "рок")
        )
        assertEquals(1L, ExperimentalAnswerRanker.rank("посоветуй фильм", answers).first().answer.id)
    }

    @Test fun contextMismatchIsFiltered() {
        val answers = listOf(AnswerElement(1, "привет", "секрет", requiredContext = "friends"))
        assertTrue(ExperimentalAnswerRanker.rank("привет", answers, userContext = "public").isEmpty())
        assertEquals(1L, ExperimentalAnswerRanker.rank("привет", answers, userContext = "FRIENDS").first().answer.id)
    }

    @Test fun emptyQueryAndZeroLimitReturnNothing() {
        val answers = listOf(AnswerElement(1, "привет", "привет"))
        assertTrue(ExperimentalAnswerRanker.rank(" ", answers).isEmpty())
        assertTrue(ExperimentalAnswerRanker.rank("привет", answers, limit = 0).isEmpty())
    }

    @Test fun tiesAreStableById() {
        val answers = listOf(AnswerElement(9, "привет", "девять"), AnswerElement(2, "привет", "два"))
        assertEquals(listOf(2L, 9L), ExperimentalAnswerRanker.rank("привет", answers).map { it.answer.id })
    }

    @Test fun synonymMappingAppliesToBothSides() {
        val answers = listOf(AnswerElement(1, "что нового", "ничего"))
        assertEquals(1L, ExperimentalAnswerRanker.rank("шо нового", answers, synonyms = mapOf("шо" to "что")).first().answer.id)
    }
}
