package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior contracts for the planned Kiro Brain text processor replacement. */
class TextAnalyzerContractTest {
    @Test fun lowercaseAndYoNormalization() {
        assertEquals("елка", TextAnalyzer.normalize("ЁЛКА"))
    }

    @Test fun unicodeWordsAndNumbersRemainTokens() {
        assertEquals(listOf("привет", "мир", "2026"), TextAnalyzer.tokens("Привет, мир! 2026", emptyMap()))
    }

    @Test fun synonymsReplaceOnlyWholeTokens() {
        val synonyms = mapOf("шо" to "что")
        assertEquals(listOf("что", "делаешь"), TextAnalyzer.tokens("Шо делаешь?", synonyms))
    }

    @Test fun punctuationAndNewlinesSeparateClauses() {
        assertEquals(listOf("Привет", "Как дела", "Спасибо"), TextAnalyzer.clauses("Привет! Как дела?\nСпасибо."))
    }

    @Test fun synonymDefinitionsSkipInvalidLines() {
        val lines = listOf("# comment", "", "что = шо, чо", "bad line", "два слова = вариант")
        assertEquals(mapOf("шо" to "что", "чо" to "что"), TextAnalyzer.parseSynonyms(lines))
    }

    @Test fun shortAndLatinWordsDoNotGetStemmed() {
        assertEquals("кот", TextAnalyzer.stem("кот"))
        assertEquals("android", TextAnalyzer.stem("android"))
    }

    @Test fun russianWordFormsShareStem() {
        assertEquals(1, listOf("работа", "работе", "работу").map(TextAnalyzer::stem).toSet().size)
    }

    @Test fun blankInputProducesNoTokensOrClauses() {
        assertTrue(TextAnalyzer.tokens("  ", emptyMap()).isEmpty())
        assertTrue(TextAnalyzer.clauses(" ! ? ").isEmpty())
    }
}
