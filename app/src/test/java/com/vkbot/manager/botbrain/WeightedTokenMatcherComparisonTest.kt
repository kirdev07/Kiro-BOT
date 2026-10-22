package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparison tests: the experimental scorer must not replace search tiers yet. */
class WeightedTokenMatcherComparisonTest {
    private val answers = listOf(
        AnswerElement(1, "привет", "здравствуй"),
        AnswerElement(2, "как дела", "хорошо"),
        AnswerElement(3, "какой язык программирования выбрать", "зависит от задачи"),
        AnswerElement(4, "посоветуй фильм", "попробуй комедию")
    )

    @Test fun exactMatchesRemainTopRankedInCurrentEngine() {
        val database = AnswerDatabase(answers)
        for (answer in answers) {
            val result = database.searchAnswers(answer.questionText, "")
            assertTrue("Missing exact match: ${answer.questionText}", result.isNotEmpty())
            assertEquals(answer.id, result.first().answer.id)
            assertEquals(1, result.first().tier)
            val tokens = TextAnalyzer.tokens(answer.questionText, emptyMap())
            assertEquals(1.0, WeightedTokenMatcher.score(tokens, tokens), 0.000001)
        }
    }

    @Test fun unrelatedQuestionHasNoExperimentalTokenOverlap() {
        val input = TextAnalyzer.tokens("погода завтра", emptyMap())
        val scores = answers.map { answer ->
            WeightedTokenMatcher.score(input, TextAnalyzer.tokens(answer.questionText, emptyMap()))
        }
        assertTrue(scores.all { it == 0.0 })
    }

    @Test fun partialTokenOverlapIsWeakerThanExactOverlap() {
        val query = TextAnalyzer.tokens("посоветуй фильм", emptyMap())
        val exact = WeightedTokenMatcher.score(query, TextAnalyzer.tokens("посоветуй фильм", emptyMap()))
        val partial = WeightedTokenMatcher.score(query, TextAnalyzer.tokens("посоветуй музыку", emptyMap()))
        assertTrue(exact > partial)
        assertTrue(partial > 0.0)
    }

    @Test fun synonymsNeedNormalizationBeforeScoring() {
        val synonyms = mapOf("шо" to "что")
        val query = TextAnalyzer.tokens("шо делаешь", synonyms)
        val candidate = TextAnalyzer.tokens("что делаешь", synonyms)
        assertEquals(1.0, WeightedTokenMatcher.score(query, candidate), 0.000001)
    }
}
