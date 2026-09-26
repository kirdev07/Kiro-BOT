package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotBrainTest {

    private fun answer(id: Long, question: String, text: String, reqCtx: String = "", resCtx: String = "") =
        AnswerElement(id = id, questionText = question, answerText = text, requiredContext = reqCtx, resultContext = resCtx)

    private fun brainOf(vararg answers: AnswerElement) = BotBrain(AnswerDatabase(answers.toList()))

    private fun ask(brain: BotBrain, text: String, userId: String = "1") =
        brain.processMessage(BotMessage(text, userId, "Тест"))?.text

    @Test
    fun exactMatchBeatsPartialMatch() {
        val brain = brainOf(
            answer(1, "как дела", "EXACT"),
            answer(2, "дела", "PARTIAL1"),
            answer(3, "как", "PARTIAL2")
        )
        // Разные пользователи, чтобы история повторов не влияла
        repeat(100) { i -> assertEquals("EXACT", ask(brain, "как дела", "u$i")) }
    }

    @Test
    fun severalExactAnswersAreChosenRandomly() {
        val brain = brainOf(
            answer(1, "уроки", "A"),
            answer(2, "уроки", "B")
        )
        val seen = (0 until 200).mapNotNull { ask(brain, "уроки", "u$it") }.toSet()
        assertEquals(setOf("A", "B"), seen)
    }

    @Test
    fun contextualAnswerPreferredWhenContextMatches() {
        val brain = brainOf(
            answer(1, "как дела", "Норм, а у тебя?", resCtx = "дела"),
            answer(2, "хорошо", "CONTEXT", reqCtx = "дела"),
            answer(3, "хорошо", "GENERAL")
        )
        assertEquals("Норм, а у тебя?", ask(brain, "как дела"))
        assertEquals("CONTEXT", ask(brain, "хорошо"))
    }

    @Test
    fun regexKeepsUppercaseEscapes() {
        // \S+ — одно слово без пробелов; после lowercase стало бы \s+ и не совпало
        val brain = brainOf(answer(1, "regex:^мой ник (\\S+)$", "Ник: {1}"))
        assertEquals("Ник: Kiro_07", ask(brain, "мой ник Kiro_07"))
    }

    @Test
    fun maskWithSpecialCharsWorks() {
        val brain = brainOf(answer(1, "курс [usd]*", "Курс:{1}"))
        assertEquals("Курс: сегодня", ask(brain, "курс [usd] сегодня"))
    }

    @Test
    fun commandWithManyVariantsUsesAllOfThem() {
        // «аниме» с 10 разными картинками: бот должен выбирать из всех, а не из 3 популярных
        val brain = brainOf(*Array(10) { i -> answer(i + 1L, "аниме", "pic$i") })
        val seen = (0 until 300).mapNotNull { ask(brain, "аниме", "u$it") }.toSet()
        assertEquals(10, seen.size)
    }

    @Test
    fun unknownQuestionsGoToUnansweredLog() {
        val brain = brainOf(answer(1, "привет", "Привет!"))
        ask(brain, "привет")
        ask(brain, "какая погода в Москве?")
        ask(brain, "Какая погода в москве")
        assertEquals(listOf(2), brain.unanswered.top().map { it.count })
        assertEquals("Какая погода в москве", brain.unanswered.top().first().text)
    }

    @Test
    fun disabledLogRecordsNothing() {
        val brain = brainOf(answer(1, "привет", "Привет!")).apply { isUnansweredLogEnabled = { false } }
        ask(brain, "какая погода в Москве?")
        assertEquals(0, brain.unanswered.size)
    }

    @Test
    fun namePlaceholderIsReplaced() {
        val brain = brainOf(answer(1, "привет", "Привет, {name}!"))
        val reply = ask(brain, "привет")
        assertTrue(reply == "Привет, Тест!")
    }
}
