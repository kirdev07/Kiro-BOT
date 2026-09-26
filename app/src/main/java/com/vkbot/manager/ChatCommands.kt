package com.vkbot.manager

import com.vkbot.manager.botbrain.UnansweredLog

/**
 * Команды из чата (начинаются с «!» или «/»).
 * - «!id» — любой может узнать свой ID (чтобы вписать его в настройки как владельца);
 * - «!запомни», «!забудь», «!незнаю», «!команды» — только владельцы из настроек,
 *   от остальных команды молча игнорируются (бот на них не отвечает).
 */
class ChatCommands(
    private val isOwner: (Long) -> Boolean,
    private val teach: (question: String, answer: String) -> Boolean,
    private val forget: (question: String) -> Int,
    private val unanswered: () -> List<UnansweredLog.Entry>,
    private val answersCount: () -> Int
) {
    sealed class Result {
        /** Обычное сообщение — искать ответ в базе. */
        object NotCommand : Result()
        /** Команда не для этого пользователя — не отвечать вовсе. */
        object Ignore : Result()
        data class Reply(val text: String) : Result()
    }

    fun handle(text: String, fromId: Long): Result {
        val t = text.trim()
        if (!t.startsWith("!") && !t.startsWith("/")) return Result.NotCommand
        // Первое слово целиком (вместе с «@имя_бота» из Telegram) — команда, остальное — аргумент.
        // Аргумент отрезается по длине слова: поиск имени в тексте ломался на «!Запомни» с заглавной
        val firstWord = t.drop(1).substringBefore(' ').substringBefore('\n')
        val name = firstWord.substringBefore('@').lowercase().replace('ё', 'е')
        val arg = t.drop(1).drop(firstWord.length).trim()

        if (name in ID_COMMANDS) return Result.Reply("Ваш ID: $fromId")
        if (name !in OWNER_COMMANDS) return Result.NotCommand
        if (!isOwner(fromId)) return Result.Ignore

        return Result.Reply(
            when (name) {
                "запомни", "запомнить" -> remember(arg)
                "забудь", "забыть" -> forgetQuestion(arg)
                "незнаю", "не_знаю" -> listUnanswered()
                else -> HELP
            }
        )
    }

    private fun remember(arg: String): String {
        val question = arg.substringBefore('=', "").trim()
        val answer = arg.substringAfter('=', "").trim()
        if (question.isEmpty() || answer.isEmpty()) return "Формат: !запомни вопрос = ответ"
        return if (teach(question, answer)) "✅ Запомнил: «$question» → «$answer». В базе ответов: ${answersCount()}"
        else "❌ Не удалось сохранить базу"
    }

    private fun forgetQuestion(question: String): String {
        if (question.isEmpty()) return "Формат: !забудь вопрос"
        return when (val removed = forget(question)) {
            -1 -> "❌ Не удалось сохранить базу"
            0 -> "Вопроса «$question» в базе нет"
            else -> "🗑 Забыл «$question» (ответов удалено: $removed)"
        }
    }

    private fun listUnanswered(): String {
        val top = unanswered().take(10)
        if (top.isEmpty()) return "Журнал «Не знаю ответа» пуст 👍"
        return "Чаще всего не знал ответа:\n" + top.mapIndexed { i, e -> "${i + 1}. ${e.text} — ×${e.count}" }.joinToString("\n") +
            "\n\nДобавить ответ: !запомни вопрос = ответ"
    }

    companion object {
        private val ID_COMMANDS = setOf("id", "айди", "мойid", "мой_id")
        private val OWNER_COMMANDS = setOf("запомни", "запомнить", "забудь", "забыть", "незнаю", "не_знаю", "команды", "помощь")

        private val HELP = """
            Команды владельца:
            !запомни вопрос = ответ — добавить ответ в базу
            !забудь вопрос — удалить все ответы на вопрос
            !незнаю — вопросы, на которые бот не знал ответа
            !id — узнать свой ID
        """.trimIndent()

        /** «123, 456 789» → {123, 456, 789} */
        fun parseOwnerIds(value: String): Set<Long> =
            value.split(',', ' ', ';', '\n').mapNotNull { it.trim().toLongOrNull() }.toSet()
    }
}
