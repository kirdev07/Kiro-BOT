package com.vkbot.manager.botbrain.conversation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Переменные в ответах: {name}, {user_id}, {time}, {date}, {last_msg}, {random}, {1}… из regex. */
object Placeholders {

    fun apply(text: String, ctx: ConversationContext, groups: List<String> = emptyList()): String {
        var result = text
        for (i in 1 until groups.size) result = result.replace("{$i}", groups[i])
        if ('{' !in result) return result
        val now = Date(ctx.now)
        return result.replace("{name}", ctx.userName)
            .replace("{user_id}", ctx.message.authorId)
            .replace("{time}", SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
            .replace("{date}", SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now))
            .replace("{last_msg}", ctx.message.text)
            .replace("{random}", ctx.random.nextInt(0, 101).toString())
    }
}
