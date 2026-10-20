package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.AnswerDatabase
import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.BotBrain
import com.vkbot.manager.botbrain.BotMessage
import com.vkbot.manager.botbrain.TextAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Регрессия разговора: многоходовые диалоги на небольшой базе и настоящем conversation.txt.
 * Время и случайность фиксированы — тесты воспроизводимы.
 */
class ConversationRegressionTest {

    private var now = 1_700_000_000_000L
    private var lastDebug: BrainDebug? = null

    private val rules = ConversationRules.parse(File("src/main/assets/conversation.txt").readLines(Charsets.UTF_8))

    private fun a(id: Long, q: String, text: String, req: String = "", res: String = "") =
        AnswerElement(id, q, text, requiredContext = req, resultContext = res)

    // Функция, а не список: счётчики использования у каждого мозга свои
    private fun baseAnswers() = listOf(
        a(1, "привет", "Привет!"), a(2, "привет", "Здравствуй!"), a(3, "привет", "Хай!"),
        a(4, "как дела", "Норм, а у тебя?", res = "дела"), a(5, "как дела", "Всё супер! Как сам?", res = "дела"),
        a(6, "хорошо", "Рад за тебя!", req = "дела"),
        a(7, "что ты умеешь", "Отвечаю на вопросы и шучу."),
        a(8, "расскажи анекдот", "Колобок повесился."),
        a(9, "regex:^мой ник (\\S+)$", "Ник: {1}"),
        a(10, "курс *", "Курс:{1}"),
        a(11, "я программист", "Круто, на чём пишешь?"),
        a(12, "что делаешь", "Отвечаю тебе 🙂")
    )

    private fun brain(answers: List<AnswerElement> = baseAnswers(), synonyms: Map<String, String> = emptyMap(), seed: Int = 7) =
        BotBrain(AnswerDatabase(answers, synonyms), rules, Random(seed)) { now }.apply { debugListener = { lastDebug = it } }

    private fun BotBrain.say(text: String, user: String = "1", chat: String = ""): String? {
        now += 5_000
        return processMessage(BotMessage(text, user, "Тест", chatId = chat))?.text
    }

    /** Ответ из раздела conversation.txt (возможно, с коротким окончанием). */
    private fun assertFrom(section: String, reply: String?, message: String = "") =
        assertTrue("$message → $reply", rules.responses(section).any { reply == it || reply?.startsWith("$it ") == true })

    private fun assertProcessor(prefix: String) = assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith(prefix) == true)

    // --- Приветствия и повторы ---

    @Test fun greetingGetsGreeting() {
        val reply = brain().say("привет")
        assertTrue(reply, reply in setOf("Привет!", "Здравствуй!", "Хай!"))
    }

    @Test fun repeatedGreetingDoesNotLoop() {
        val b = brain()
        val replies = (1..3).map { b.say("привет") }
        assertEquals("три приветствия подряд — три разных ответа", 3, replies.toSet().size)
    }

    @Test fun greetingDoesNotOverrideMainQuestion() {
        val reply = brain().say("привет, как дела")
        assertTrue(reply, reply!!.contains("Норм") || reply.contains("Всё супер"))
    }

    // --- Короткие продолжения ---

    @Test fun mirrorFollowUpUsesPreviousQuestion() {
        val b = brain()
        val first = b.say("как дела?")
        val second = b.say("а ты?")
        assertProcessor("продолжение: а ты?")
        assertNotEquals(first, second)
        assertTrue(second, second in setOf("Норм, а у тебя?", "Всё супер! Как сам?"))
    }

    @Test fun contextualAnswerStillWins() {
        val b = brain()
        b.say("как дела")
        assertEquals("Рад за тебя!", b.say("хорошо"))
    }

    @Test fun yesAfterFreshReplyUsesFollowUp() {
        val b = brain()
        b.say("что ты умеешь")
        val reply = b.say("да")
        assertProcessor("продолжение: follow_yes")
        assertFrom("follow_yes", reply)
    }

    @Test fun shortRepliesUseTheirOwnSection() {
        for ((text, section) in listOf("почему?" to "follow_why", "серьёзно?" to "follow_really", "ясно" to "follow_ack", "и что?" to "follow_so", "неа" to "follow_no")) {
            val b = brain()
            b.say("расскажи анекдот")
            val reply = b.say(text)
            assertFrom(section, reply, text)
        }
    }

    @Test fun oldContextIsNotGluedToShortReply() {
        val b = brain()
        b.say("что ты умеешь")
        now += DialogHistory.FOLLOW_UP_TTL + 1
        b.say("да")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith("продолжение") != true)
    }

    @Test fun mirrorWithoutPreviousQuestionUsesGenericAnswer() {
        val b = brain()
        b.say("расскажи анекдот")
        val reply = b.say("а ты?")
        assertFrom("follow_you", reply)
    }

    @Test fun historyIsSeparatePerChatAndUser() {
        val b = brain()
        b.say("как дела?", user = "1", chat = "100")
        // Другой собеседник в той же беседе — для него «а ты?» без контекста
        b.say("а ты?", user = "2", chat = "100")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor != "продолжение: а ты?")
        // Тот же собеседник, но в другом чате — тоже без контекста
        b.say("а ты?", user = "1", chat = "200")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor != "продолжение: а ты?")
    }

    // --- База: команды, regex, маски ---

    @Test fun regexAndMasksStillWork() {
        val b = brain()
        assertEquals("Ник: Kiro_07", b.say("мой ник Kiro_07"))
        assertEquals("Курс:сегодня", b.say("курс сегодня"))
    }

    @Test fun unknownQuestionGoesToLogAndFallback() {
        val b = brain()
        val reply = b.say("сколько стоит билет в Москву")
        assertEquals("запасной ответ", lastDebug?.processor)
        assertTrue(reply!!.isNotEmpty())
        assertEquals(1, b.unanswered.size)
    }

    @Test fun unknownQuestionGoesToAiWhenAvailable() {
        val b = brain().apply { aiResponder = { _, _ -> "ответ ИИ" } }
        assertEquals("ответ ИИ", b.say("сколько стоит билет в Москву"))
        assertEquals("ИИ", lastDebug?.processor)
        assertEquals("ответ ИИ", b.unanswered.top().first().suggestion)
    }

    @Test fun typosStillFound() {
        assertEquals("Круто, на чём пишешь?", brain().say("я програмист"))
    }

    @Test fun synonymsStillWork() {
        val synonyms = TextAnalyzer.parseSynonyms(listOf("что = че, чо, шо"))
        assertEquals("Отвечаю тебе 🙂", brain(synonyms = synonyms).say("шо делаешь"))
    }


    // --- Реакции ---

    @Test fun thanksGetsPositiveReaction() {
        val reply = brain().say("спасибо большое")
        assertProcessor("реакция: positive")
        assertFrom("positive", reply)
    }

    @Test fun insultGetsCalmNegativeReaction() {
        val reply = brain().say("ты тупой")
        assertProcessor("реакция: negative")
        assertFrom("negative", reply)
    }

    @Test fun negationIsNotInsult() {
        brain().say("ты не тупой")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith("реакция") != true)
    }

    @Test fun baseAnswerBeatsReaction() {
        val b = brain(baseAnswers() + a(20, "ты тупой", "Сам такой!"))
        assertEquals("Сам такой!", b.say("ты тупой"))
    }

    @Test fun reactionsDoNotRepeat() {
        val b = brain()
        val replies = (1..4).map { b.say("спасибо") }
        assertEquals(4, replies.toSet().size)
    }

    // --- Функции ---

    @Test fun teaOrCoffee() {
        val reply = brain().say("чай или кофе?")!!
        assertProcessor("функция: или")
        assertTrue(reply, reply.contains("чай") || reply.contains("кофе"))
    }

    @Test fun chancePercent() {
        val reply = brain().say("инфа что завтра повезет")!!
        assertProcessor("функция: инфа")
        assertTrue(reply, Regex("\\d{1,3}%").containsMatchIn(reply))
    }

    @Test fun mathAndRandomAndWhen() {
        val b = brain()
        assertTrue(b.say("сколько будет 2+2*2")!!.contains("= 6"))
        assertTrue(b.say("рандом 1 10")!!.let { r -> Regex("\\b([1-9]|10)\\b").containsMatchIn(r) })
        b.say("когда я стану миллионером")
        assertProcessor("функция: когда")
    }

    @Test fun exactBaseCommandBeatsFunction() {
        val b = brain(baseAnswers() + a(21, "рандом", "Своя команда рандома"))
        assertEquals("Своя команда рандома", b.say("рандом"))
    }

    @Test fun functionUnderstandsAppealAndGreeting() {
        val reply = brain().say("Бот, привет, сколько будет 10*3")!!
        assertTrue(reply, reply.contains("= 30"))
    }

    // --- Несколько вопросов в сообщении ---

    @Test fun threeQuestionsGiveAtMostTwoShortAnswers() {
        val reply = brain().say("Привет. Как дела? Что ты умеешь?")!!
        assertTrue(reply, reply.contains("Отвечаю на вопросы") && (reply.contains("Норм") || reply.contains("Всё супер")))
        assertTrue("приветствие второстепенно: $reply", listOf("Привет!", "Здравствуй!", "Хай!").none { reply.contains(it) })
    }

    @Test fun greetingAndQuestionAreJoinedNaturally() {
        val reply = brain().say("Привет! Что ты умеешь?")!!
        assertTrue(reply, reply.endsWith("Отвечаю на вопросы и шучу.") && listOf("Привет!", "Здравствуй!", "Хай!").any { reply.startsWith(it) })
    }

    @Test fun longAnswersAreNotJoined() {
        val long = "Очень длинный рассказ о том, что я умею. ".repeat(5).trim()
        val b = brain(baseAnswers().filter { it.id != 7L } + a(7, "что ты умеешь", long))
        assertEquals(long, b.say("Привет. Что ты умеешь?"))
    }

    @Test fun numbersAndLinksDoNotSplitMessage() {
        val b = brain()
        b.say("Смотри https://kiro.ru/a.b?x=1 и число 3.14")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.contains("части") != true)
    }

    // --- Память диалога ---

    @Test fun rememberedNameIsUsed() {
        val b = brain(baseAnswers() + a(22, "как тебя зовут", "Я Kiro, а ты {name}"))
        val saved = b.say("меня зовут кирилл")!!
        assertTrue(saved, saved.contains("Кирилл"))
        assertTrue(b.say("как меня зовут?")!!.contains("Кирилл"))
        assertEquals("Я Kiro, а ты Кирилл", b.say("как тебя зовут"))
    }

    @Test fun nameIsNotSharedBetweenUsersAndCanBeForgotten() {
        val b = brain()
        b.say("меня зовут Кирилл", user = "1", chat = "100")
        b.say("как меня зовут", user = "2", chat = "100")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith("память") != true)
        b.say("забудь меня", user = "1", chat = "100")
        assertProcessor("память диалога: fact_forget")
        b.say("как меня зовут", user = "1", chat = "100")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith("память") != true)
    }

    @Test fun onlyExplicitNamesAreRemembered() {
        val b = brain()
        b.say("меня зовут никак")
        b.say("мой номер 89991234567")
        b.say("как меня зовут")
        assertTrue("${lastDebug?.processor}", lastDebug?.processor?.startsWith("память") != true)
    }

    // --- Неизвестные вопросы и ИИ ---

    @Test fun similarUnknownQuestionsAreGrouped() {
        val b = brain()
        b.say("сколько стоит билет в Москву")
        b.say("сколько стоит билет до Москвы?")
        b.say("ok")
        assertEquals(1, b.unanswered.size)
        assertEquals(2, b.unanswered.top().first().count)
    }

    @Test fun functionAnswersAreNotLoggedAsUnknown() {
        val b = brain()
        b.say("чай или кофе")
        b.say("спасибо")
        assertEquals(0, b.unanswered.size)
    }

    @Test fun aiAnswerBecomesPartOfHistory() {
        val b = brain().apply { aiResponder = { _, _ -> "Столица Франции — Париж." } }
        b.say("какая столица франции")
        val reply = b.say("серьёзно?")
        assertFrom("follow_really", reply)
    }

    @Test fun aiSeesWholeConversationIncludingBaseAnswers() {
        var seen: List<DialogHistory.Turn> = emptyList()
        val b = brain().apply { aiResponder = { _, turns -> seen = turns; "Около 68 миллионов." } }
        b.say("как дела")
        b.say("а какое население во франции")
        assertEquals("как дела", seen.single().userText)
        assertTrue(seen.single().botText.isNotEmpty())
    }

    /** Мозг на настоящей базе answer.txt (15 000 строк) — там оценки совпадений как у пользователя. */
    private fun realBrain() = BotBrain(AnswerDatabase(
        File("src/main/assets/answer.txt").readLines(Charsets.UTF_8).map { it.removePrefix(Char(0xFEFF).toString()).trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapIndexedNotNull { i, l -> l.split("|").takeIf { it.size >= 2 }?.let { p -> a(i + 1L, p[0].trim(), p[1].trim()) } },
        TextAnalyzer.parseSynonyms(File("src/main/assets/synonyms.txt").readLines(Charsets.UTF_8))
    ), rules, Random(7)) { now }.apply { debugListener = { lastDebug = it } }

    @Test fun unsureBaseMatchYieldsToAiWhenEnabled() {
        // На настоящей базе это сообщение совпадает только с общим «ужасно» (оценка 0.44)
        val text = "привет, слушай, у меня сегодня был ужасный день на работе, начальник наорал"
        val withAi = realBrain().apply { aiAvailable = { true }; aiResponder = { _, _ -> "Сочувствую! Что случилось?" } }
        assertEquals("Сочувствую! Что случилось?", withAi.say(text))
        // ИИ не ответил — отложенный ответ базы, а не запасная фраза
        val aiFails = realBrain().apply { aiAvailable = { true }; aiResponder = { _, _ -> null } }
        aiFails.say(text)
        assertProcessor("база: по смыслу (ИИ не ответил)")
        // Без ИИ — сразу база
        realBrain().say(text)
        assertProcessor("база: по смыслу")
    }

    @Test fun preciseQuestionBeatsGenericOneWord() {
        // «что такое фотосинтез» (0.63) точнее общего «объясни» (0.57) — найдено в Telegram
        repeat(10) { assertEquals("что такое фотосинтез", AnswerSelector.selectionPool(
            AnswerDatabase(listOf(a(1, "что такое фотосинтез", "A"), a(2, "объясни", "B"), a(3, "двух", "C"), a(4, "словах", "D")))
                .searchAnswers("объясни что такое фотосинтез в двух словах", "")).first().answer.questionText) }
    }

    @Test fun confidentBaseMatchBeatsAi() {
        val b = brain().apply { aiAvailable = { true }; aiResponder = { _, _ -> "ИИ" } }
        assertEquals("Круто, на чём пишешь?", b.say("я программист"))
    }

    // --- Отладка ---

    @Test fun debugExplainsChoice() {
        val b = brain()
        b.say("как дела")
        val d = lastDebug!!
        assertEquals(1, d.tier)
        assertTrue(d.candidates.isNotEmpty() && d.candidates.size <= 3)
        assertTrue(d.format(), d.format().contains("база: точно"))
        b.say("сколько стоит билет в Москву")
        assertTrue(lastDebug!!.reason, lastDebug!!.reason.contains("нет ответа"))
        assertTrue(lastDebug!!.tried.size >= 5)
    }

    @Test fun debugIsNotSentToUser() {
        val b = brain()
        val reply = b.say("как дела")!!
        assertTrue(reply, !reply.contains("🧠") && !reply.contains("уровень"))
    }
}
