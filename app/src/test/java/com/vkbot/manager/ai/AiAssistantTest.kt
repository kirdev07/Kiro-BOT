package com.vkbot.manager.ai

import com.vkbot.manager.botbrain.AnswerDatabase
import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.BotBrain
import com.vkbot.manager.botbrain.BotMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantTest {

    private class FakeUsage(var count: Int = 0) : AiAssistant.DailyUsage {
        override fun today() = count
        override fun increment() { count++ }
    }

    private val calls = mutableListOf<List<AiTurn>>()
    private var created = 0
    private var answer = "**Привет!** Я бот."
    private var fail: String? = null

    private var config = AiConfig(enabled = true, provider = AiProvider.CLAUDE, model = "claude-opus-5", apiKey = "k", dailyLimit = 3)
    private val usage = FakeUsage()
    private val logs = mutableListOf<String>()

    private val assistant = AiAssistant(
        config = { config },
        usage = usage,
        onLog = { logs.add(it) },
        clientFactory = {
            created++
            object : AiClient {
                override fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String {
                    calls.add(history)
                    fail?.let { throw AiException(it) }
                    return answer
                }
            }
        }
    )

    private fun ask(text: String = "как дела?", group: Boolean = false) = assistant.reply("u1", "Аня", text, group)

    @Test fun answersAndCleansMarkdown() {
        assertEquals("Привет! Я бот.", ask())
        assertEquals(1, usage.count)
    }

    @Test fun silentWhenDisabledIncompleteOrInGroupChats() {
        config = config.copy(enabled = false); assertNull(ask())
        config = config.copy(enabled = true, apiKey = ""); assertNull(ask())
        config = config.copy(apiKey = "k"); assertNull(ask(group = true))
        config = config.copy(inChats = true); assertEquals("Привет! Я бот.", ask(group = true))
        assertEquals(1, calls.size)
    }

    @Test fun openAiCompatibleWithoutKeyButWithUrlIsComplete() {
        assertTrue(AiConfig(provider = AiProvider.OPENAI_COMPATIBLE, model = "llama", baseUrl = "http://localhost:11434/v1").isComplete)
        assertTrue(!AiConfig(provider = AiProvider.OPENAI_COMPATIBLE, model = "llama").isComplete)
        assertTrue(!AiConfig(provider = AiProvider.YANDEX, model = "yandexgpt-lite", apiKey = "k").isComplete)
    }

    @Test fun dailyLimitStopsAndLogsOnce() {
        usage.count = 3
        assertNull(ask()); assertNull(ask())
        assertEquals(1, logs.count { it.contains("лимит") })
        assertTrue(calls.isEmpty())
    }

    @Test fun historyIsSentOnNextMessage() {
        ask("привет"); ask("а как тебя зовут?")
        val second = calls[1]
        assertEquals(listOf(true, false, true), second.map { it.fromUser })
        assertEquals("привет", second[0].text)
        assertEquals("а как тебя зовут?", second.last().text)
    }

    @Test fun errorGivesNullAndLog() {
        fail = "неверный API-ключ Claude"
        assertNull(ask())
        assertEquals(0, usage.count)
        assertTrue(logs.any { it.contains("неверный API-ключ") })
    }

    @Test fun clientIsReusedUntilSettingsChange() {
        ask(); ask()
        assertEquals(1, created)
        config = config.copy(model = "claude-haiku-4-5")
        ask()
        assertEquals(2, created)
    }

    @Test fun cleanTrimsAtSentenceBoundary() {
        assertEquals("Первое предложение.", AiAssistant.clean("Первое предложение. Второе очень длинное предложение", 30))
        assertEquals("Одно длинное слово без точк…", AiAssistant.clean("Одно длинное слово без точки вообще", 27))
        assertEquals("Заголовок\nтекст", AiAssistant.clean("## Заголовок\n`текст`", 100))
    }

    @Test fun cleanRemovesThinkingOfReasoningModels() {
        assertEquals("Привет!", AiAssistant.clean("<think>\nпользователь здоровается…\n</think>\n\nПривет!", 100))
        assertEquals("", AiAssistant.clean("<think>не успел дописать", 100))
    }

        @Test fun systemPromptHasPersonaRulesAndName() {
        val p = AiAssistant.systemPrompt(config.copy(persona = "Ты пират.", maxChars = 150), "Аня")
        assertTrue(p.startsWith("Ты пират."))
        assertTrue(p.contains("150 символов") && p.contains("Аня"))
    }

    @Test fun testButtonExplainsMissingFields() {
        try {
            assistant.test(AiConfig(provider = AiProvider.OPENAI_COMPATIBLE, model = "m"))
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("адрес API"))
        }
    }

    // --- Встраивание в мозг бота ---

    @Test fun brainAsksAiOnlyWhenBaseHasNoAnswer() {
        val brain = BotBrain(AnswerDatabase(listOf(AnswerElement(1, "привет", "Привет из базы"))))
        val asked = mutableListOf<String>()
        brain.aiResponder = { m -> asked.add(m.text); "Ответ ИИ" }

        assertEquals("Привет из базы", brain.processMessage(BotMessage("привет", "1", "Аня"))?.text)
        assertEquals("Ответ ИИ", brain.processMessage(BotMessage("какая погода на Марсе", "1", "Аня"))?.text)
        assertEquals(listOf("какая погода на Марсе"), asked)
        assertEquals("Ответ ИИ", brain.unanswered.top().single().suggestion)
    }
}
