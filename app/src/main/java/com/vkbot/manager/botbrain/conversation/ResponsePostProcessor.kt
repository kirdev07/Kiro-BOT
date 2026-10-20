package com.vkbot.manager.botbrain.conversation

/**
 * Итоговая обработка ответа перед отправкой:
 * - лишние пробелы и пустые строки (код в ``` и отступы в начале строк не трогаем);
 * - заглавная первая буква, если первое слово — обычное слово, а не ссылка, файл, @ник или {переменная};
 * - иногда короткое окончание (раздел [suffix]) — только к разговорным реакциям и продолжениям,
 *   не к ответам базы, командам, медиа и ИИ.
 */
object ResponsePostProcessor {

    /** Вероятность окончания у разговорного ответа: редко, чтобы не превращать всё в шаблон. */
    const val SUFFIX_CHANCE = 0.15
    private val SUFFIX_KINDS = setOf(ReplyKind.REACTION, ReplyKind.FOLLOW_UP)

    private val INNER_SPACES = Regex("(?<=\\S)[ \\t]{2,}(?=\\S)")
    private val MANY_NEWLINES = Regex("\\n{3,}")
    private val NOT_A_WORD = Regex("://|www\\.|[@/\\\\_{}#]|\\p{L}\\.\\p{L}")

    fun process(reply: Reply, ctx: ConversationContext): Reply {
        var text = normalizeWhitespace(reply.text)
        if (reply.kind != ReplyKind.AI) text = capitalizeSafely(text)
        if (reply.kind in SUFFIX_KINDS && reply.attachments.isEmpty() && ctx.random.nextDouble() < SUFFIX_CHANCE) {
            text = withSuffix(text, ctx)
        }
        return if (text == reply.text) reply else reply.copy(text = text)
    }

    fun normalizeWhitespace(text: String): String {
        var inCode = false
        val lines = text.replace("\r\n", "\n").split('\n').map { line ->
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (inCode || line.trimStart().startsWith("```")) line.trimEnd() else line.replace(INNER_SPACES, " ").trimEnd()
        }
        return lines.joinToString("\n").replace(MANY_NEWLINES, "\n\n").trim('\n', ' ')
    }

    fun capitalizeSafely(text: String): String {
        val first = text.firstOrNull() ?: return text
        if (!first.isLetter() || !first.isLowerCase()) return text
        val firstToken = text.substringBefore(' ').substringBefore('\n')
        if (NOT_A_WORD.containsMatchIn(firstToken)) return text
        return first.uppercaseChar() + text.substring(1)
    }

    private fun withSuffix(text: String, ctx: ConversationContext): String {
        val last = text.lastOrNull() ?: return text
        // Уже кончается смайлом — второй не нужен
        if (!last.isLetterOrDigit() && last !in ".!?,)»\"") return text
        val lastBot = ctx.dialog.lastBotResponse().orEmpty()
        val options = ctx.rules.responses("suffix").filter { !lastBot.endsWith(it) }
        if (options.isEmpty()) return text
        return "$text ${options.random(ctx.random)}"
    }
}
