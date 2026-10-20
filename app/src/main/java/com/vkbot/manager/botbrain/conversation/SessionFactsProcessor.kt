package com.vkbot.manager.botbrain.conversation

/**
 * Память диалога: имя, которое собеседник сам явно назвал («меня зовут Кирилл», «зови меня Кир»).
 * Ничего не извлекается само по себе — только прямое «меня зовут / зови меня / моё имя».
 * Живёт [DialogHistory.FACT_TTL], хранится отдельно для каждого собеседника в каждом чате, на диск не пишется.
 * «Забудь меня» / «забудь моё имя» — очистить.
 */
class SessionFactsProcessor : ConversationProcessor, ConversationObserver {
    override val name = "память диалога"
    override val priority = Priority.SESSION_FACTS

    /** Что произошло с этим сообщением (заполняет [observe], отвечает [process]). */
    private val events = ThreadLocal<String?>()

    override fun observe(ctx: ConversationContext) {
        events.set(null)
        val words = ctx.coreWords
        if (words.size > MAX_WORDS) return
        when {
            FORGET.any { it == words } -> { ctx.dialog.clearFacts(); events.set(FORGOTTEN) }
            else -> nameFrom(ctx.core)?.let { ctx.dialog.setFact(NAME, it); events.set(SAVED) }
        }
    }

    override fun process(ctx: ConversationContext): Reply? {
        val section = events.get() ?: if (ASK_NAME.any { it == ctx.coreWords }) {
            if (ctx.dialog.fact(NAME) != null) KNOWN else return null
        } else return null
        val text = ctx.rules.pick(section, ctx.random, ctx.dialog::turnsSinceSaid) ?: return null
        return Reply(Placeholders.apply(text, ctx), ReplyKind.FACT, "$name: $section")
    }

    companion object {
        const val NAME = "name"
        const val MAX_WORDS = 6
        private const val SAVED = "fact_name_saved"
        private const val KNOWN = "fact_name_known"
        private const val FORGOTTEN = "fact_forget"

        private val NAME_PATTERN = Regex(
            "^(?:меня зовут|зови меня|называй меня|мо[её] имя|я\\s*-\\s*|можешь звать меня)\\s+([\\p{L}][\\p{L}-]{1,19})(?:\\s+([\\p{L}][\\p{L}-]{1,19}))?[.!)]*$",
            RegexOption.IGNORE_CASE
        )
        private val NOT_A_NAME = setOf("никак", "секрет", "бот", "ботом", "не", "тебя", "тебе", "так", "это", "никто", "как", "просто", "по")

        private val ASK_NAME = listOf("как меня зовут", "ты помнишь как меня зовут", "помнишь как меня зовут", "как мое имя",
            "помнишь мое имя", "кто я").map { ConversationRules.words(it) }
        private val FORGET = listOf("забудь меня", "забудь мое имя", "забудь как меня зовут").map { ConversationRules.words(it) }

        /** Имя из прямого «меня зовут X» (одно-два слова из букв), иначе null. */
        fun nameFrom(text: String): String? {
            val m = NAME_PATTERN.find(text.trim()) ?: return null
            val parts = listOfNotNull(m.groupValues[1], m.groupValues[2].takeIf { it.isNotEmpty() })
            if (parts.any { it.lowercase().replace('ё', 'е') in NOT_A_NAME }) return null
            return parts.joinToString(" ") { p -> p.lowercase().replaceFirstChar { it.titlecase() } }
        }
    }
}
