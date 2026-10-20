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

class PersonalityPacksTest {

    private fun lines(name: String) = File("src/main/assets/$name").readLines(Charsets.UTF_8)
    private val base = ConversationRules.parse(lines(ConversationRules.FILE_NAME))
    private fun pack(id: String) = ConversationRules.parse(lines(ConversationRules.personalityFile(id)))

    /** Разделы с шаблонами ответов (кроме [greeting] — там только триггеры). */
    private val responseSections = listOf("follow_yes", "follow_no", "follow_why", "follow_really", "follow_so", "follow_ack", "follow_you",
        "positive", "negative", "fact_name_saved", "fact_name_known", "fact_forget", "choice", "chance", "when", "time", "date",
        "math", "math_error", "random")

    @Test fun everyPackFileExistsAndCoversAllSections() {
        for (id in ConversationRules.PERSONALITIES.filter { it.isNotEmpty() }) {
            val merged = base.overriddenBy(pack(id))
            for (s in responseSections) {
                assertTrue("$id: [$s] пуст", merged.responses(s).isNotEmpty())
                assertTrue("$id: [$s] не заменён набором", merged.responses(s) != base.responses(s))
            }
            // Триггеры остаются из основного файла
            assertEquals(base.triggers("positive"), merged.triggers("positive"))
        }
    }

    @Test fun templatesKeepTheirPlaceholders() {
        for (id in ConversationRules.PERSONALITIES.filter { it.isNotEmpty() }) {
            val p = pack(id)
            for (s in listOf("choice", "chance", "when", "time", "date", "math", "random")) {
                assertTrue("$id: [$s] без {x}", p.responses(s).all { "{x}" in it } || s == "when")
            }
            assertTrue("$id: имя", (p.responses("fact_name_saved") + p.responses("fact_name_known")).all { "{name}" in it })
        }
    }

    @Test fun emptyPackSectionMeansNoPhrases() {
        val merged = base.overriddenBy(pack("polite"))
        assertTrue(base.responses("suffix").isNotEmpty())
        assertTrue(merged.responses("suffix").isEmpty())
    }

    @Test fun chosenPackChangesReplies() {
        val rules = base.overriddenBy(pack("polite"))
        var now = 1_000_000L
        val brain = BotBrain(AnswerDatabase(listOf(AnswerElement(1, "привет", "Привет!"))), rules, Random(1)) { now }
        repeat(20) { i ->
            now += 1000
            val reply = brain.processMessage(BotMessage("спасибо", "u$i", "Тест"))!!.text
            assertTrue(reply, reply in pack("polite").responses("positive"))
        }
    }
}
