package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quality probes for the experimental scorer. They document current limitations. */
class ExperimentalSearchQualityTest {
    private val answers = listOf(
        AnswerElement(1, "привет", "привет!"),
        AnswerElement(2, "как дела", "нормально"),
        AnswerElement(3, "посоветуй фильм", "комедия"),
        AnswerElement(4, "помоги с работой", "что случилось?")
    )

    @Test fun punctuationAndCaseDoNotChangeExactRanking() {
        val plain = ExperimentalAnswerRanker.rank("привет", answers)
        val decorated = ExperimentalAnswerRanker.rank("ПрИвЕт!!!", answers)
        assertEquals(plain.map { it.answer.id }, decorated.map { it.answer.id })
    }

    @Test fun longMessageKeepsRelevantCandidateInResults() {
        val ranked = ExperimentalAnswerRanker.rank("слушай, я сегодня устал на работе, помоги с работой", answers)
        assertTrue(ranked.any { it.answer.id == 4L })
    }

    @Test fun synonymNormalizedSlangStillMatches() {
        val candidates = listOf(AnswerElement(10, "что делаешь", "работаю"))
        val ranked = ExperimentalAnswerRanker.rank("шо делаешь", candidates, mapOf("шо" to "что"))
        assertEquals(10L, ranked.first().answer.id)
    }

    @Test fun typoDoesNotMatchWithoutFuzzyStage() {
        val ranked = ExperimentalAnswerRanker.rank("превет", listOf(answers[0]))
        assertTrue("Known limitation: exact token overlap has no typo correction", ranked.isEmpty())
    }

    @Test fun unknownWordsDoNotCreateFalseMatches() {
        assertTrue(ExperimentalAnswerRanker.rank("космодром", answers).isEmpty())
    }
}
