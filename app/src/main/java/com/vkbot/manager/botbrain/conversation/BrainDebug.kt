package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.SearchResult

/**
 * Почему бот ответил именно так. Только для лога / экрана разработчика — собеседнику не отправляется.
 */
data class BrainDebug(
    val message: String,
    val processor: String,
    val kind: ReplyKind?,
    /** Уровень и оценка выбранного ответа базы (null — ответ не из базы). */
    val tier: Int?,
    val score: Float?,
    val context: String,
    /** Три лучших кандидата из базы на всё сообщение. */
    val candidates: List<SearchResult>,
    /** Какие обработчики успели посмотреть сообщение. */
    val tried: List<String>,
    /** Почему ушли в ИИ / запасной ответ. */
    val reason: String = "",
    /** Настроение бота к собеседнику (в режиме «по настроению»). */
    val mood: String = "",
    /** Каким характером ответили. */
    val personality: String = ""
) {
    /** Одна строка для лога. */
    fun format(): String = buildString {
        append("🧠 «").append(message.take(60)).append("» → ").append(processor)
        if (tier != null) append(" [уровень ").append(tier).append(", оценка ").append("%.2f".format(score ?: 0f)).append("]")
        if (context.isNotEmpty()) append(" · контекст: ").append(context)
        if (mood.isNotEmpty()) append(" · настроение ").append(mood)
        if (personality.isNotEmpty()) append(" · характер: ").append(personality)
        if (reason.isNotEmpty()) append(" · ").append(reason)
        if (candidates.isNotEmpty()) {
            append(" · кандидаты: ")
            append(candidates.joinToString("; ") { "«${it.answer.questionText.take(30)}» ${it.tier}/${"%.2f".format(it.score)}" })
        }
    }
}
