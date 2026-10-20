package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.AnswerDatabase
import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.BotBrain
import com.vkbot.manager.botbrain.BotMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MoodTest {

    private var now = 1_000_000L
    private var lastDebug: BrainDebug? = null

    private fun parse(name: String) = ConversationRules.parse(File("src/main/assets/$name").readLines(Charsets.UTF_8))
    private val base = parse(ConversationRules.FILE_NAME)
    private val packs = ConversationRules.PERSONALITIES.filter { it.isNotEmpty() }.associateWith { parse(ConversationRules.personalityFile(it)) }

    private fun brain(auto: Boolean = true) =
        BotBrain(AnswerDatabase(listOf(AnswerElement(1, "как дела", "Норм"))), base, Random(3), presetPacks = packs) { now }.apply {
            autoMood = { auto }
            debugListener = { lastDebug = it }
        }

    private fun BotBrain.say(text: String, user: String = "1"): String {
        now += 10_000
        return processMessage(BotMessage(text, user, "Тест"))!!.text
    }

    /** Ответ из раздела набора (возможно, с коротким окончанием). */
    private fun from(pack: String, section: String, reply: String) =
        (if (pack.isEmpty()) base else packs.getValue(pack)).responses(section).any { reply == it || reply.startsWith("$it ") }

    @Test fun moodDecaysByHalfIn20Minutes() {
        val d = DialogHistory { now }.dialog("", "1")
        d.changeMood(0.8)
        now += DialogHistory.MOOD_HALF_LIFE
        assertEquals(0.4, d.mood(), 1e-9)
        d.changeMood(10.0)
        assertEquals(1.0, d.mood(), 1e-9)
    }

    @Test fun thresholds() {
        assertEquals("warm", Mood.personalityFor(0.6))
        assertEquals("", Mood.personalityFor(0.0))
        assertEquals("polite", Mood.personalityFor(-0.3))
        assertEquals("cheeky", Mood.personalityFor(-0.8))
    }

    @Test fun insultsMakeBotReservedThenCheeky() {
        val b = brain()
        val first = b.say("ты тупой")
        assertTrue(first, from("polite", "negative", first))
        val second = b.say("ты тупой бот")
        assertTrue(second, from("cheeky", "negative", second))
        assertEquals("cheeky", lastDebug?.personality)
        assertTrue(lastDebug!!.format(), lastDebug!!.format().contains("настроение 😠"))
    }

    @Test fun praiseMakesBotWarm() {
        val b = brain()
        b.say("спасибо")
        val reply = b.say("ты классный")
        assertTrue(reply, from("warm", "positive", reply))
    }

    @Test fun moodCoolsDownWithTime() {
        val b = brain()
        b.say("ты тупой"); b.say("ты тупой")
        now += 3 * DialogHistory.MOOD_HALF_LIFE
        b.say("как дела")
        assertEquals("", lastDebug?.personality)
    }

    @Test fun moodIsPerUser() {
        val b = brain()
        b.say("ты тупой", user = "troll"); b.say("отвали", user = "troll")
        val reply = b.say("спасибо", user = "friend")
        assertTrue(reply, from("", "positive", reply))
    }

    @Test fun manualChoiceWhenAutoMoodIsOff() {
        val b = brain(auto = false).apply { personality = { "polite" } }
        repeat(3) { b.say("ты тупой") }
        assertEquals("polite", lastDebug?.personality)
        assertEquals("", lastDebug?.mood)
    }
}
