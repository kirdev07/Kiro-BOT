package com.vkbot.manager.botbrain.conversation

enum class Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

/** Эмоциональная окраска короткого сообщения по фразам из разделов [positive] / [negative]. */
object SentimentAnalyzer {

    /** Слова, которые не меняют смысла реакции: «спасибо большое, бот» = «спасибо». */
    private val FILLER = setOf("бот", "ботик", "ты", "тебе", "тебя", "очень", "большое", "прям", "прямо", "такой", "такая",
        "же", "ну", "а", "вот", "просто", "какой", "какая", "ж", "всё", "все", "reallly", "киро")

    /** Длинное сообщение — уже не реакция, а рассказ. */
    const val MAX_WORDS = 8
    /** Фраза-реакция должна составлять не меньше половины значимых слов сообщения. */
    const val MIN_COVERAGE = 0.5

    fun classify(words: List<String>, rules: ConversationRules): Sentiment {
        if (words.isEmpty() || words.size > MAX_WORDS) return Sentiment.NEUTRAL
        val meaningful = words.count { it !in FILLER }.coerceAtLeast(1)
        val positive = coverage(words, rules.triggers(POSITIVE), meaningful)
        val negative = coverage(words, rules.triggers(NEGATIVE), meaningful)
        return when {
            positive >= MIN_COVERAGE && positive > negative -> Sentiment.POSITIVE
            negative >= MIN_COVERAGE && negative > positive -> Sentiment.NEGATIVE
            else -> Sentiment.NEUTRAL
        }
    }

    /** Доля значимых слов сообщения, покрытая лучшей фразой; фраза после «не» («не тупой») не считается. */
    private fun coverage(words: List<String>, triggers: List<List<String>>, meaningful: Int): Double =
        triggers.maxOfOrNull { t ->
            val at = indexOfSublist(words, t)
            if (at < 0 || (at > 0 && words[at - 1] == "не")) 0.0
            else t.count { it !in FILLER }.coerceAtLeast(1).toDouble() / meaningful
        } ?: 0.0

    private fun indexOfSublist(words: List<String>, part: List<String>): Int {
        if (part.isEmpty() || part.size > words.size) return -1
        for (i in 0..words.size - part.size) if (words.subList(i, i + part.size) == part) return i
        return -1
    }

    const val POSITIVE = "positive"
    const val NEGATIVE = "negative"
}

/** Похвала или ругань в адрес бота — вариативная реакция. Уступает базе, если там есть точный ответ. */
class SentimentProcessor : ConversationProcessor {
    override val name = "реакция"
    override val priority = Priority.SENTIMENT

    override fun process(ctx: ConversationContext): Reply? {
        val section = when (SentimentAnalyzer.classify(ctx.words, ctx.rules)) {
            Sentiment.POSITIVE -> SentimentAnalyzer.POSITIVE
            Sentiment.NEGATIVE -> SentimentAnalyzer.NEGATIVE
            Sentiment.NEUTRAL -> return null
        }
        // В базе есть уверенный ответ по смыслу — он точнее общей реакции
        if (ctx.search.any { it.tier <= LAST_STRONG_TIER && it.score >= STRONG_SCORE }) return null
        val text = ctx.rules.pick(section, ctx.random, ctx.dialog::turnsSinceSaid) ?: return null
        return Reply(text, ReplyKind.REACTION, "$name: $section")
    }

    companion object {
        const val STRONG_SCORE = 0.85f
    }
}
