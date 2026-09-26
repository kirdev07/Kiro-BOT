package com.vkbot.manager.botbrain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Поиск по реальной базе из assets: скорость и отсутствие ошибок. */
class SearchPerformanceTest {

    private fun loadRealDatabase(): AnswerDatabase {
        val file = File("src/main/assets/answer.txt")
        var id = 1L
        val answers = file.readLines(Charsets.UTF_8)
            .map { it.removePrefix(Char(0xFEFF).toString()).trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 2) null
                else AnswerElement(id = id++, questionText = parts[0].trim(), answerText = parts[1].trim())
            }
        return AnswerDatabase(answers)
    }

    @Test
    fun searchOverRealDatabaseIsFast() {
        val db = loadRealDatabase()
        assertTrue(db.answersCount > 10_000)

        val queries = listOf(
            "привет", "как дела", "ты кто такой", "что ты умеешь делать",
            "расскажи анекдот про программиста", "как тебя зовут и сколько тебе лет",
            "мне скучно давай поговорим", "ты знаешь какая сегодня погода в москве",
            "почему интернет тупит", "я тебя люблю"
        )
        repeat(3) { queries.forEach { db.searchAnswers(it, "") } } // прогрев JIT

        val rounds = 20
        val start = System.nanoTime()
        repeat(rounds) { queries.forEach { db.searchAnswers(it, "") } }
        val avgMs = (System.nanoTime() - start) / 1_000_000.0 / (rounds * queries.size)

        println("SEARCH_AVG_MS=%.2f".format(avgMs))
        // Телефон в 3–5 раз медленнее ПК; 20 мс на ПК ≈ до 100 мс на телефоне
        assertTrue("Поиск слишком медленный: $avgMs мс", avgMs < 20.0)
    }
}
