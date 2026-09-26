package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Пользователь пишет свою маленькую базу (например, для магазина в сообществе).
 * Поиск должен понимать живые сообщения клиентов и без огромной базы.
 */
class CustomDatabaseTest {

    private val questions = listOf(
        "сколько стоит доставка" to "DELIVERY_PRICE",
        "как оформить заказ" to "ORDER",
        "где вы находитесь" to "ADDRESS",
        "режим работы" to "HOURS",
        "есть ли скидки" to "DISCOUNT",
        "как вернуть товар" to "RETURN",
        "какие способы оплаты" to "PAYMENT",
        "привет" to "HELLO",
        "спасибо" to "THANKS"
    )

    private val db = AnswerDatabase(
        questions.mapIndexed { i, (q, a) -> AnswerElement(id = i + 1L, questionText = q, answerText = a) },
        TextAnalyzer.parseSynonyms(listOf("спасибо = спс, благодарю", "привет = здравствуйте, здрасте, добрый"))
    )

    private fun reply(message: String): String? {
        val results = db.searchAnswers(message, "")
        if (results.isEmpty()) return null
        val tier = results.minOf { it.tier }
        return results.filter { it.tier == tier }.maxByOrNull { it.score }?.answer?.answerText
    }

    @Test fun longPoliteQuestion() =
        assertEquals("DELIVERY_PRICE", reply("Здравствуйте! Подскажите пожалуйста, сколько у вас стоит доставка до Москвы?"))

    @Test fun otherWordsSameMeaning() = assertEquals("ORDER", reply("как сделать заказ?"))

    @Test fun wordForms() = assertEquals("DISCOUNT", reply("а скидка есть?"))

    @Test fun shortForm() = assertEquals("HOURS", reply("до скольки работаете? какой режим работы"))

    @Test fun returnGoods() = assertEquals("RETURN", reply("хочу вернуть товар, он сломался"))

    @Test fun payment() = assertEquals("PAYMENT", reply("можно оплатить картой? какие есть способы оплаты"))

    @Test fun thanksSlang() = assertEquals("THANKS", reply("спс большое"))

    @Test fun greetingThenQuestion() = assertEquals("ADDRESS", reply("добрый день, а где вы находитесь?"))

    @Test fun unrelatedMessageGetsNoAnswer() = assertEquals(null, reply("какая завтра погода в питере"))
}
