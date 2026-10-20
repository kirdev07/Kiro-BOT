package com.vkbot.manager.botbrain.conversation

/**
 * Настроение бота → характер фраз (режим «Менять характер по настроению»).
 * Похвала поднимает настроение, ругань опускает; ругань действует сильнее — обиду помнят дольше.
 */
object Mood {
    /** Насколько поднимает настроение одна похвала. */
    const val PRAISE = 0.35
    /** Насколько опускает одна грубость. */
    const val INSULT = -0.45

    /** Выше — «Тёплый». */
    const val WARM_FROM = 0.5
    /** Ниже — «Вежливый» (держит дистанцию). */
    const val POLITE_BELOW = -0.2
    /** Ниже — «Дерзкий» (огрызается). */
    const val CHEEKY_BELOW = -0.6

    fun deltaFor(sentiment: Sentiment): Double = when (sentiment) {
        Sentiment.POSITIVE -> PRAISE
        Sentiment.NEGATIVE -> INSULT
        Sentiment.NEUTRAL -> 0.0
    }

    /** id набора характера для настроения (см. [ConversationRules.PERSONALITIES]). */
    fun personalityFor(mood: Double): String = when {
        mood >= WARM_FROM -> "warm"
        mood < CHEEKY_BELOW -> "cheeky"
        mood < POLITE_BELOW -> "polite"
        else -> ""
    }

    /** Для лога: «😊 0.60». */
    fun describe(mood: Double): String {
        val face = when (personalityFor(mood)) { "warm" -> "😊"; "polite" -> "😐"; "cheeky" -> "😠"; else -> "🙂" }
        return "$face %.2f".format(java.util.Locale.ROOT, mood)
    }
}
