package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightedTokenMatcherTest {
    @Test fun identicalTokensHavePerfectScore() {
        assertEquals(1.0, WeightedTokenMatcher.score(listOf("привет", "друг"), listOf("друг", "привет")), 0.000001)
    }

    @Test fun disjointTokensHaveZeroScore() {
        assertEquals(0.0, WeightedTokenMatcher.score(listOf("привет"), listOf("пока")), 0.0)
    }

    @Test fun blankInputsHaveZeroScore() {
        assertEquals(0.0, WeightedTokenMatcher.score(emptyList(), listOf("тест")), 0.0)
        assertEquals(0.0, WeightedTokenMatcher.score(listOf("  "), listOf("тест")), 0.0)
    }

    @Test fun duplicatesDoNotInflateScore() {
        assertEquals(1.0, WeightedTokenMatcher.score(listOf("кот", "кот"), listOf("кот")), 0.0)
    }

    @Test fun partialOverlapScoresBetweenZeroAndOne() {
        val score = WeightedTokenMatcher.score(listOf("кот", "дома"), listOf("кот", "спит"))
        assertTrue(score > 0.0 && score < 1.0)
    }

    @Test fun zeroWeightDoesNotProduceNaN() {
        assertEquals(0.0, WeightedTokenMatcher.score(listOf("кот"), listOf("кот"), mapOf("кот" to 0.0)), 0.0)
    }
}
