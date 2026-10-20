package com.vkbot.manager.botbrain.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {

    @Test fun splitsQuestions() =
        assertEquals(listOf("Привет.", "Как дела?", "Что ты умеешь?"), SentenceSplitter.split("Привет. Как дела? Что ты умеешь?"))

    @Test fun keepsNumbersLinksAndAbbreviations() {
        assertEquals(listOf("Число 3.14 и 1,5 — это дроби."), SentenceSplitter.split("Число 3.14 и 1,5 — это дроби."))
        assertEquals(listOf("Смотри https://example.com/a.b?c=1 тут.", "Круто?"), SentenceSplitter.split("Смотри https://example.com/a.b?c=1 тут. Круто?"))
        assertEquals(listOf("Зайди на kiro.ru сегодня"), SentenceSplitter.split("Зайди на kiro.ru сегодня"))
        assertEquals(listOf("Фрукты, т.е. яблоки и т.д. полезны."), SentenceSplitter.split("Фрукты, т.е. яблоки и т.д. полезны."))
        assertEquals(listOf("Встреча в 10:30"), SentenceSplitter.split("Встреча в 10:30"))
    }

    @Test fun ellipsisNewLinesAndEmptyParts() {
        assertEquals(listOf("ну…", "ладно"), SentenceSplitter.split("ну… ладно"))
        assertEquals(listOf("первая", "вторая"), SentenceSplitter.split("первая\n\nвторая"))
        assertEquals(listOf("что?!"), SentenceSplitter.split("что?! ... !!!"))
    }
}
