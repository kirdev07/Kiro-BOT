package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.SearchResult

/** Выбирает ответ базы, учитывает его использование и контекст диалога. */
class DatabaseResponder(private val recordUsage: (AnswerElement) -> Unit) {

    fun answer(ctx: ConversationContext, candidates: List<SearchResult>, processor: String, exclude: Set<String> = emptySet()): Reply? {
        val chosen = AnswerSelector.select(candidates, ctx.dialog, ctx.random, exclude) ?: return null
        commit(ctx, chosen)
        return Reply(textOf(ctx, chosen), ReplyKind.DATABASE, processor, chosen.answer.answerAttachments, chosen)
    }

    fun commit(ctx: ConversationContext, chosen: SearchResult) {
        recordUsage(chosen.answer)
        ctx.dialog.countRepetition(chosen.answer.id)
        ctx.dialog.setContext(chosen.answer.resultContext)
    }

    fun textOf(ctx: ConversationContext, sr: SearchResult): String = Placeholders.apply(sr.answer.answerText, ctx, sr.capturedGroups)
}

/** Уровни поиска (см. AnswerDatabase): чётные — ответы с совпавшим контекстом диалога. */
private val CONTEXT_TIERS = setOf(0, 2)
private val EXACT_AND_COMMAND_TIERS = setOf(1, 3)
/** Уровни 4–5 — уверенное совпадение по смыслу, 6–7 — слабое. */
internal const val LAST_STRONG_TIER = 5

/** Ответы, продолжающие контекст диалога (requiredContext совпал): автор базы задал их явно. */
class DatabaseContextProcessor(private val responder: DatabaseResponder) : ConversationProcessor {
    override val name = "база: контекст"
    override val priority = Priority.DATABASE_CONTEXT
    override fun process(ctx: ConversationContext) = responder.answer(ctx, ctx.search.filter { it.tier in CONTEXT_TIERS }, name)
}

/** Точное совпадение фразы и команды (regex / маски). */
class DatabaseExactProcessor(private val responder: DatabaseResponder) : ConversationProcessor {
    override val name = "база: точно / команда"
    override val priority = Priority.DATABASE_EXACT
    override fun process(ctx: ConversationContext) = responder.answer(ctx, ctx.search.filter { it.tier in EXACT_AND_COMMAND_TIERS }, name)
}

/**
 * Совпадение по смыслу слов. Сообщение из нескольких предложений («Привет. Как дела? Что умеешь?»)
 * разбирается по частям: отвечаем на главный вопрос и, если оба ответа короткие, добавляем второй.
 * Приветствие — второстепенная часть.
 */
class DatabaseSmartProcessor(private val responder: DatabaseResponder) : ConversationProcessor {
    override val name = "база: по смыслу"
    override val priority = Priority.DATABASE_SMART

    override fun process(ctx: ConversationContext): Reply? {
        val parts = SentenceSplitter.split(ctx.text)
        if (parts.size >= 2) multiPart(ctx, parts)?.let { return it }
        val smart = ctx.search.filter { it.tier > EXACT_AND_COMMAND_TIERS.max() }
        // ИИ ответит лучше, чем база по случайному совпадению слова; ответ базы — запасной
        if (ctx.aiAvailable && isUnsure(smart)) {
            ctx.deferred = smart
            return null
        }
        return responder.answer(ctx, smart, name)
    }

    private fun isUnsure(results: List<SearchResult>): Boolean {
        val pool = AnswerSelector.selectionPool(results)
        return pool.isNotEmpty() && (pool.first().tier > LAST_STRONG_TIER || pool.maxOf { it.score } < AI_PREFERRED_BELOW)
    }

    private class Part(val index: Int, val greeting: Boolean, val results: List<SearchResult>) {
        val bestTier = results.minOf { it.tier }
        val bestScore = results.filter { it.tier == bestTier }.maxOf { it.score }
    }

    private fun multiPart(ctx: ConversationContext, parts: List<String>): Reply? {
        val analyzed = parts.take(MAX_PARTS).mapIndexedNotNull { i, text ->
            val results = ctx.searchFor(text).filter { it.tier <= LAST_STRONG_TIER }
            if (results.isEmpty()) null else Part(i, Intent.isGreeting(ConversationRules.words(text), ctx.rules), results)
        }
        val questions = analyzed.filter { !it.greeting }.sortedWith(compareBy<Part> { it.bestTier }.thenByDescending { it.bestScore })
        val main = questions.firstOrNull() ?: return null
        val second = questions.getOrNull(1) ?: analyzed.firstOrNull { it.greeting }

        val mainChoice = AnswerSelector.select(main.results, ctx.dialog, ctx.random) ?: return null
        val secondChoice = second?.let { AnswerSelector.select(it.results, ctx.dialog, ctx.random, exclude = setOf(mainChoice.answer.answerText)) }
            ?.takeIf { isShort(mainChoice) && isShort(it) }

        responder.commit(ctx, mainChoice)
        if (secondChoice == null || second == null) {
            return Reply(responder.textOf(ctx, mainChoice), ReplyKind.DATABASE, "$name (главная часть)", mainChoice.answer.answerAttachments, mainChoice)
        }
        responder.commit(ctx, secondChoice)
        ctx.dialog.setContext(mainChoice.answer.resultContext)
        val ordered = listOf(main to mainChoice, second to secondChoice).sortedBy { it.first.index }
        val text = ordered.joinToString(" ") { responder.textOf(ctx, it.second) }
        return Reply(text, ReplyKind.DATABASE, "$name (две части)", emptyList(), mainChoice)
    }

    /** Объединяются только короткие ответы без вложений. */
    private fun isShort(sr: SearchResult) = sr.answer.answerAttachments.isEmpty() && sr.answer.answerText.length <= SHORT_ANSWER

    companion object {
        /** Больше трёх частей не разбираем — хвост длинного сообщения обычно пояснения. */
        const val MAX_PARTS = 3
        const val SHORT_ANSWER = 150
        /** Совпадение по смыслу слабее этого при включённом ИИ уступает ему («ужасный день» ≈ «ужасно» 0.44). */
        const val AI_PREFERRED_BELOW = 0.5f
    }
}
