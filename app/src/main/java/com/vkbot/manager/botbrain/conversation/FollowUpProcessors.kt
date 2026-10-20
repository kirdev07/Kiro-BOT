package com.vkbot.manager.botbrain.conversation

/**
 * «А ты?» / «а у тебя?» сразу после вопроса собеседника: бот отвечает на тот же вопрос про себя.
 * «Как дела?» → «Норм, а у тебя?» → «а ты?» → другой ответ на «как дела».
 */
class MirrorFollowUpProcessor(private val responder: DatabaseResponder) : ConversationProcessor {
    override val name = "продолжение: а ты?"
    override val priority = Priority.MIRROR_FOLLOW_UP

    override fun process(ctx: ConversationContext): Reply? {
        if (!ctx.rules.matchesWhole(FollowUpProcessor.YOU, ctx.words)) return null
        val last = ctx.dialog.lastTurn(DialogHistory.FOLLOW_UP_TTL) ?: return null
        // Зеркалим только настоящий вопрос, а не другое «а ты?»
        if (!Intent.isQuestion(last.userText) || ctx.rules.matchesWhole(FollowUpProcessor.YOU, ConversationRules.words(last.userText))) return null
        val candidates = ctx.searchFor(last.userText).filter { it.tier <= LAST_STRONG_TIER }
        return responder.answer(ctx, candidates, name, exclude = setOf(last.botText))?.copy(kind = ReplyKind.FOLLOW_UP)
    }
}

/**
 * Короткие продолжения без смысла вне разговора: «да», «нет», «почему?», «серьёзно?», «ясно», «и что?».
 * Срабатывают, только если бот ответил совсем недавно, и если база не ответила на них сама.
 */
class FollowUpProcessor : ConversationProcessor {
    override val name = "продолжение"
    override val priority = Priority.FOLLOW_UP

    override fun process(ctx: ConversationContext): Reply? {
        if (ctx.words.size > MAX_WORDS) return null
        val section = SECTIONS.firstOrNull { ctx.rules.matchesWhole(it, ctx.words) } ?: return null
        ctx.dialog.lastTurn(DialogHistory.FOLLOW_UP_TTL) ?: return null
        val text = ctx.rules.pick(section, ctx.random, ctx.dialog::turnsSinceSaid) ?: return null
        return Reply(text, ReplyKind.FOLLOW_UP, "$name: $section")
    }

    companion object {
        const val YOU = "follow_you"
        val SECTIONS = listOf("follow_yes", "follow_no", "follow_why", "follow_really", "follow_so", "follow_ack", YOU)
        const val MAX_WORDS = 4
    }
}
