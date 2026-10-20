package com.vkbot.manager.botbrain.conversation.functions

import com.vkbot.manager.botbrain.BotMessage
import com.vkbot.manager.botbrain.conversation.ConversationContext
import com.vkbot.manager.botbrain.conversation.ConversationRules
import com.vkbot.manager.botbrain.conversation.DialogHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import kotlin.random.Random

class FunctionsTest {

    /** Вторник, 29 сентября 2026, 14:05 UTC. */
    private val now = 1_790_690_700_000L
    private val utc = { TimeZone.getTimeZone("UTC") }

    private fun ctx(text: String, seed: Int = 1) = ConversationContext(
        BotMessage(text, "1", "Тест"), DialogHistory { now }.dialog("", "1"), ConversationRules.EMPTY, Random(seed), now
    ) { _, _ -> emptyList() }

    /** Без conversation.txt функция отвечает просто значением {x}. */
    private fun FunctionProcessor.answer(text: String, seed: Int = 1) = process(ctx(text, seed))?.text

    // --- или … или ---

    @Test fun choicePicksOneOfOptions() {
        val seen = (1..40).mapNotNull { ChoiceFunction().answer("чай или кофе?", it) }.toSet()
        assertEquals(setOf("чай", "кофе"), seen)
        assertTrue(ChoiceFunction().answer("Бот, что лучше: пицца, суши или бургер?") in setOf("пицца", "суши", "бургер"))
    }

    @Test fun choiceIgnoresLongTextsAndSingleOption() {
        assertNull(ChoiceFunction().answer("как ты думаешь стоит ли мне учить питон или лучше джаву для начала своего пути"))
        assertNull(ChoiceFunction().answer("или"))
        assertNull(ChoiceFunction().answer("привет как дела"))
    }

    // --- инфа ---

    @Test fun chanceIsPercentAndStableForSameQuestion() {
        val a = ChanceFunction().answer("инфа что завтра повезет")!!.toInt()
        assertTrue(a in 0..100)
        assertEquals(a, ChanceFunction().answer("Инфа, что завтра повезёт?", seed = 99)!!.toInt())
        assertTrue(ChanceFunction().answer("какова вероятность дождя")!!.toInt() in 0..100)
        assertNull(ChanceFunction().answer("инфа"))
    }

    // --- когда ---

    @Test fun whenOnlyForPersonalQuestions() {
        val reply = WhenFunction().answer("когда я стану богатым?")!!
        assertTrue(reply, Regex("\\d+ (день|дня|дней|неделю|недели|недель|месяц|месяца|месяцев|год|года|лет)").matches(reply))
        assertNull("настоящий вопрос — в базу / ИИ", WhenFunction().answer("когда началась вторая мировая война"))
    }

    @Test fun russianPlurals() {
        val day = listOf("день", "дня", "дней")
        assertEquals(listOf("день", "дня", "дней", "дней", "день", "дня"),
            listOf(1, 2, 5, 11, 21, 22).map { WhenFunction.plural(it, day) })
    }

    // --- время и дата ---

    @Test fun timeAndDate() {
        assertEquals("14:05", TimeFunction(utc).answer("который час?"))
        assertEquals("14:05", TimeFunction(utc).answer("Бот, сколько времени"))
        assertEquals("вторник, 29 сентября 2026", DateFunction(utc).answer("какое сегодня число?"))
        assertNull(DateFunction(utc).answer("какой сегодня день был ужасный"))
    }

    // --- калькулятор ---

    @Test fun mathEvaluatesSafely() {
        assertEquals("2+2*2 = 6", MathFunction().answer("сколько будет 2+2*2"))
        assertEquals("(5 + 3) / 2 = 4", MathFunction().answer("посчитай (5 + 3) / 2"))
        assertEquals("2^10 = 1024", MathFunction().answer("2^10"))
        assertEquals("10 : 4 = 2.5", MathFunction().answer("10 : 4 =?"))
        assertEquals("1,5 x 2 = 3", MathFunction().answer("посчитай 1,5 x 2"))
    }

    @Test fun mathRejectsNonExpressions() {
        assertNull("дата", MathFunction().answer("29/09/2026"))
        assertNull("телефон", MathFunction().answer("+7 999 123 45 67"))
        assertNull("диапазон без «посчитай»", MathFunction().answer("10-15"))
        assertNull("не выражение", MathFunction().answer("сколько тебе лет"))
        assertNull("мусор", MathFunction().answer("посчитай 2++"))
        assertNull("огромное", MathFunction().answer("посчитай 9^99^99"))
        assertNull("вызов кода", MathFunction().answer("посчитай Runtime.exec(1)"))
        assertEquals("деление на ноль", "", MathFunction().answer("5/0"))
    }

    @Test fun calculatorLimits() {
        assertNull(SafeCalculator.evaluate("(".repeat(30) + "1" + ")".repeat(30)))
        assertNull(SafeCalculator.evaluate("1".repeat(100)))
        assertEquals("0.333333", SafeCalculator.format(SafeCalculator.evaluate("1/3")!!))
        assertEquals("-3", SafeCalculator.format(SafeCalculator.evaluate("-(1+2)")!!))
    }

    // --- рандом ---

    @Test fun randomInRange() {
        repeat(30) { seed ->
            assertTrue(RandomFunction().answer("рандом 5 10", seed)!!.toLong() in 5..10)
            assertTrue(RandomFunction().answer("случайное число от 10 до 1", seed)!!.toLong() in 1..10)
            assertTrue(RandomFunction().answer("брось кубик", seed)!!.toLong() in 1..6)
            assertTrue(RandomFunction().answer("рандом 1-3", seed)!!.toLong() in 1..3)
        }
        assertTrue(RandomFunction().answer("рандом")!!.toLong() in 1..100)
    }

    @Test fun randomRejectsBadInput() {
        assertNull(RandomFunction().answer("рандом аниме"))
        assertNull(RandomFunction().answer("случайно разбил телефон"))
        assertNull(RandomFunction().answer("рандом 1 99999999999999999"))
        assertNull(RandomFunction().answer("рандом 1 2 3"))
    }
}
