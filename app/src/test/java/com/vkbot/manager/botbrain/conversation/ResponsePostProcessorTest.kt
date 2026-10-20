package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.Attachment
import com.vkbot.manager.botbrain.BotMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ResponsePostProcessorTest {

    private val rules = ConversationRules.parse(listOf("[suffix]", "😊", "🙂"))

    private fun ctx(seed: Int = 1) = ConversationContext(
        BotMessage("x", "1", "Тест"), DialogHistory { 0L }.dialog("", "1"), rules, Random(seed), 0L
    ) { _, _ -> emptyList() }

    private fun process(text: String, kind: ReplyKind = ReplyKind.DATABASE, seed: Int = 1, attachments: List<Attachment> = emptyList()) =
        ResponsePostProcessor.process(Reply(text, kind, "t", attachments), ctx(seed)).text

    @Test fun removesDoubleSpacesAndExtraNewLines() {
        assertEquals("Привет, как дела?", process("Привет,   как  дела?  "))
        assertEquals("Раз\n\nДва", process("Раз\n\n\n\n Два".replace("\n Два", "\nДва")))
    }

    @Test fun keepsIndentationAndCode() {
        val code = "Пример:\n```\nval  x =   1\n```"
        assertEquals(code, process(code))
        assertEquals("Список:\n    - пункт", process("Список:\n    - пункт"))
    }

    @Test fun capitalizesOnlyPlainWords() {
        assertEquals("Ага, понял", process("ага, понял"))
        assertEquals("https://kiro.ru — сайт", process("https://kiro.ru — сайт"))
        assertEquals("file.txt лежит в папке", process("file.txt лежит в папке"))
        assertEquals("{name}, привет", ResponsePostProcessor.capitalizeSafely("{name}, привет"))
        assertEquals("@kiro_bot тут", process("@kiro_bot тут"))
        assertEquals("ai пишет как хочет", process("ai пишет как хочет", ReplyKind.AI))
    }

    @Test fun suffixIsRareAndOnlyForConversationalReplies() {
        val withSuffix = (1..400).count { seed -> process("Супер", ReplyKind.REACTION, seed) != "Супер" }
        assertTrue("$withSuffix", withSuffix in 30..100)
        assertTrue((1..400).all { seed -> process("Супер", ReplyKind.DATABASE, seed) == "Супер" })
        assertTrue((1..400).all { seed -> process("Готово", ReplyKind.AI, seed) == "Готово" })
        assertTrue((1..400).all { seed -> process("Лови", ReplyKind.REACTION, seed, listOf(Attachment.parse("photo1_2")!!)) == "Лови" })
        assertTrue("смайл не удваивается", (1..400).all { seed -> process("Спасибо 😊", ReplyKind.REACTION, seed) == "Спасибо 😊" })
    }
}
