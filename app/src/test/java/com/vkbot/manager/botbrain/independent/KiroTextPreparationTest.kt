package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

class KiroTextPreparationTest {
    @Test fun normalizesRussianText() {
        assertEquals("привет, мир!", KiroTextPreparation.normalize("  ПРИВЁТ, МИР!  "))
    }

    @Test fun extractsWordsAndNumbers() {
        assertEquals(listOf("киро", "бот", "3"), KiroTextPreparation.words("КИРО-бот 3"))
    }

    @Test fun emptyInput() {
        assertEquals(emptyList<String>(), KiroTextPreparation.words("  ...  "))
    }
}
