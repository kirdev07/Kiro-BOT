package com.vkbot.manager.botbrain.conversation.functions

import com.vkbot.manager.botbrain.conversation.ConversationContext
import com.vkbot.manager.botbrain.conversation.ConversationProcessor
import com.vkbot.manager.botbrain.conversation.Priority
import com.vkbot.manager.botbrain.conversation.Reply
import com.vkbot.manager.botbrain.conversation.ReplyKind

/**
 * Лёгкая локальная функция: распознаёт свою фразу и возвращает раздел conversation.txt и значение {x}.
 * Если раздела в файле нет (старый файл пользователя) — отвечает просто значением.
 */
abstract class FunctionProcessor : ConversationProcessor {
    override val priority = Priority.FUNCTIONS

    /** Ответ функции: раздел с шаблонами и подставляемое значение. */
    protected class Result(val section: String, val value: String)

    /** null — фраза не для этой функции. */
    protected abstract fun evaluate(ctx: ConversationContext): Result?

    override fun process(ctx: ConversationContext): Reply? {
        val result = evaluate(ctx) ?: return null
        val template = ctx.rules.pick(result.section, ctx.random, ctx.dialog::turnsSinceSaid) ?: "{x}"
        return Reply(template.replace("{x}", result.value), ReplyKind.FUNCTION, "функция: $name")
    }

    companion object {
        /** Совпадает ли начало фразы с одним из вариантов (по словам). @return остаток фразы (исходный текст) или null */
        internal fun afterPrefix(text: String, words: List<String>, prefixes: List<List<String>>): String? {
            val prefix = prefixes.filter { words.size >= it.size && words.subList(0, it.size) == it }.maxByOrNull { it.size } ?: return null
            // Отрезаем столько же слов исходного текста; остаток — с исходными знаками («(5+3)/2»)
            val spans = Regex("[\\p{L}\\p{N}]+").findAll(text).toList()
            return text.substring(spans[prefix.size - 1].range.last + 1).trimStart(' ', ',', ':', ';', '!', '.', '—').trim()
        }
    }
}
