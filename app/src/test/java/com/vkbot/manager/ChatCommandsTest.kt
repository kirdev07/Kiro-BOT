package com.vkbot.manager

import com.vkbot.manager.botbrain.AndroidFileManager
import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.UnansweredLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandsTest {

    private val OWNER = 42L
    private val taught = mutableListOf<Pair<String, String>>()
    private val forgotten = mutableListOf<String>()
    private val log = UnansweredLog().apply { record("погода"); record("погода"); record("курс") }

    private val commands = ChatCommands(
        isOwner = { it == OWNER },
        teach = { q, a -> taught.add(q to a); true },
        forget = { q -> forgotten.add(q); if (q == "привет") 2 else 0 },
        unanswered = { log.top() },
        answersCount = { 100 }
    )

    private fun reply(text: String, from: Long = OWNER) = (commands.handle(text, from) as ChatCommands.Result.Reply).text

    @Test fun ordinaryMessageIsNotCommand() {
        assertEquals(ChatCommands.Result.NotCommand, commands.handle("привет, как дела", OWNER))
        assertEquals(ChatCommands.Result.NotCommand, commands.handle("!неизвестная", OWNER))
    }

    @Test fun anyoneCanLearnTheirId() = assertEquals("Ваш ID: 777", reply("!id", from = 777))

    @Test fun ownerTeachesWithEqualsSign() {
        assertTrue(reply("!запомни привет = Здарова!").startsWith("✅"))
        assertEquals(listOf("привет" to "Здарова!"), taught)
    }

    @Test fun multilineAnswerAndTelegramSlashWithBotName() {
        reply("/запомни@kiro_bot как дела = Норм\nа у тебя?")
        assertEquals(listOf("как дела" to "Норм\nа у тебя?"), taught)
    }

    @Test fun capitalizedCommandAndYoWork() {
        // С телефона первая буква часто заглавная автоматически
        reply("!Запомни Привет = Здарова")
        reply("!ЗАБУДЬ пока")
        assertEquals(listOf("Привет" to "Здарова"), taught)
        assertEquals(listOf("пока"), forgotten)
    }

    @Test fun strangerCommandsAreIgnoredSilently() {
        assertEquals(ChatCommands.Result.Ignore, commands.handle("!запомни мат = плохое", 13))
        assertEquals(ChatCommands.Result.Ignore, commands.handle("!забудь привет", 13))
        assertTrue(taught.isEmpty() && forgotten.isEmpty())
    }

    @Test fun badFormatExplainsUsage() {
        assertEquals("Формат: !запомни вопрос = ответ", reply("!запомни без знака равно"))
        assertEquals("Формат: !забудь вопрос", reply("!забудь"))
        assertTrue(taught.isEmpty())
    }

    @Test fun forgetReportsCount() {
        assertTrue(reply("!забудь привет").contains("удалено: 2"))
        assertTrue(reply("!забудь нечто").contains("нет"))
    }

    @Test fun unansweredListShowsTopWithCounts() {
        val text = reply("!незнаю")
        assertTrue(text.contains("1. погода — ×2"))
        assertTrue(text.contains("2. курс — ×1"))
    }

    @Test fun ownerIdsParsing() =
        assertEquals(setOf(123L, 456L, 789L), ChatCommands.parseOwnerIds(" 123, 456 789; abc "))

    @Test fun baseEditsAddAndRemoveByMeaning() {
        val base = listOf(AnswerElement(1, "Привет!", "a"), AnswerElement(5, "пока", "b"), AnswerElement(7, "привет", "c"))
        val added = AndroidFileManager.withAnswer(base, " погода ", " солнце ")
        assertEquals(AnswerElement(8, "погода", "солнце"), added.last())
        val (kept, removed) = AndroidFileManager.withoutQuestion(base, "ПРИВЕТ")
        assertEquals(2, removed)
        assertEquals(listOf("пока"), kept.map { it.questionText })
    }
}
