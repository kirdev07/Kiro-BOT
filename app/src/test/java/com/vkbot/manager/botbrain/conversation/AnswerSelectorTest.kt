package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnswerSelectorTest {

    private val history = DialogHistory { 0L }
    private val random = Random(42)

    private fun sr(id: Long, text: String, tier: Int = 1, score: Float = 1f, usage: Int = 0, limit: Int = 0) =
        SearchResult(AnswerElement(id, "q", text, usageCount = usage, repetitionLimit = limit), tier = tier, score = score)

    @Test fun recentAnswerIsNotRepeatedEvenForOtherQuestion() {
        val d = history.dialog("", "1")
        d.addTurn("хай", "Привет!", "база")
        val chosen = (1..50).map { AnswerSelector.select(listOf(sr(1, "Привет!"), sr(2, "Здравствуй!")), d, random)!!.answer.answerText }
        assertEquals(setOf("Здравствуй!"), chosen.toSet())
    }

    @Test fun whenAllWereSaidTheOldestIsRepeated() {
        val d = history.dialog("", "1")
        d.addTurn("1", "A", "база")
        d.addTurn("2", "B", "база")
        assertEquals("A", AnswerSelector.select(listOf(sr(1, "A"), sr(2, "B")), d, random)!!.answer.answerText)
    }

    @Test fun randomnessNeverBeatsMuchMoreRelevantAnswer() {
        val d = history.dialog("", "1")
        val candidates = listOf(sr(1, "лучший", tier = 5, score = 1f), sr(2, "слабый", tier = 5, score = 0.5f), sr(3, "другой уровень", tier = 7))
        repeat(50) { assertEquals("лучший", AnswerSelector.select(candidates, history.dialog("", "u$it"), random)!!.answer.answerText) }
    }

    @Test fun relaxedAlternativeInsteadOfRepeat() {
        val d = history.dialog("", "1")
        d.addTurn("q", "лучший", "база")
        val candidates = listOf(sr(1, "лучший", tier = 5, score = 1f), sr(2, "почти", tier = 5, score = 0.8f), sr(3, "слабый", tier = 5, score = 0.3f))
        assertEquals("почти", AnswerSelector.select(candidates, d, random)!!.answer.answerText)
    }

    @Test fun rarelyUsedVariantsAreSlightlyMoreLikely() {
        val candidates = listOf(sr(1, "частый", usage = 1000), sr(2, "редкий", usage = 0))
        val rare = (0 until 4000).count { AnswerSelector.select(candidates, history.dialog("", "u$it"), random)!!.answer.answerText == "редкий" }
        // Веса 1.001 и 2 → редкий примерно в 2/3 случаев, но частый тоже звучит
        assertTrue("$rare", rare in 2400..2950)
    }

    @Test fun repetitionLimitIsRespected() {
        val d = history.dialog("", "1")
        d.countRepetition(1)
        assertNull(AnswerSelector.select(listOf(sr(1, "один раз", limit = 1)), d, random))
    }
}
