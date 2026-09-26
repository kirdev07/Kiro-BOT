package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsTest {

    private fun freshAnswers() = listOf(
        AnswerElement(id = 1, questionText = "Привет", answerText = "Здравствуй"),
        AnswerElement(id = 2, questionText = "привет", answerText = "Хай"),
        AnswerElement(id = 3, questionText = "пока", answerText = "До встречи")
    )

    @Test
    fun usageCountsSurviveReload() {
        val db = AnswerDatabase(freshAnswers())
        val brain = BotBrain(db)
        repeat(30) { brain.processMessage(BotMessage("привет", "u$it", "Тест")) }

        val before = db.searchAnswers("привет", "").associate { it.answer.answerText to it.answer.usageCount }
        assertEquals(30, before.values.sum())

        // «Сохранение» и загрузка в новые элементы (id после перезагрузки другие)
        val stats = AndroidFileManager.collectUsageStats(db.searchAnswers("привет", "").map { it.answer })
        val reloaded = freshAnswers().mapIndexed { i, e -> e.copy(id = 100L + i) }
        AndroidFileManager.applyUsageStats(reloaded, stats)

        val after = reloaded.associate { it.answerText to it.usageCount }
        assertEquals(before["Здравствуй"], after["Здравствуй"])
        assertEquals(before["Хай"], after["Хай"])
        assertEquals(0, after["До встречи"])
    }
}
