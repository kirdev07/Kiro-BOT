package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Качество поиска на реальной базе answer.txt: длинные сообщения, формы слов, синонимы. */
class SmartSearchTest {

    companion object {
        private lateinit var db: AnswerDatabase

        @BeforeClass @JvmStatic
        fun loadRealDatabase() {
            var id = 1L
            val answers = File("src/main/assets/answer.txt").readLines(Charsets.UTF_8)
                .map { it.removePrefix(Char(0xFEFF).toString()).trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val p = line.split("|")
                    if (p.size < 2) null else AnswerElement(id = id++, questionText = p[0].trim(), answerText = p[1].trim())
                }
            val synonyms = TextAnalyzer.parseSynonyms(File("src/main/assets/synonyms.txt").readLines(Charsets.UTF_8))
            db = AnswerDatabase(answers, synonyms)
        }

        /** Вопросы из базы, среди которых бот выбирает ответ (лучший уровень совпадения). */
        fun chosenQuestions(message: String): Set<String> {
            return BotBrain.selectionPool(db.searchAnswers(message, "")).map { it.answer.questionText }.toSet()
        }
    }

    private fun assertAnswersAbout(message: String, vararg anyOf: String) {
        val chosen = chosenQuestions(message)
        assertTrue("«$message» → $chosen, ожидалось про ${anyOf.toList()}",
            chosen.isNotEmpty() && chosen.all { q -> anyOf.any { q.contains(it) } })
    }

    @Test fun longMessageAboutBadDayAtWork() =
        assertAnswersAbout("привет, слушай, у меня сегодня был ужасный день на работе, начальник наорал", "работ", "начальник", "ужас")

    @Test fun longQuestionAboutProgrammingLanguages() =
        assertAnswersAbout("как ты думаешь стоит ли мне учить питон или лучше джаву для начала", "питон", "джав")

    @Test fun letterYoIsSameAsYe() = assertAnswersAbout("чё каво", "че каво")

    @Test fun slangSynonyms() = assertAnswersAbout("шо делаешь", "что делаешь", "че делаешь")

    @Test fun wordFormsMatch() {
        // «программистом» → «программист», «работаю» → «работ»
        val chosen = chosenQuestions("я работаю программистом")
        assertTrue("$chosen", "я программист" in chosen)
    }

    @Test fun stemmerUnifiesWordForms() {
        val forms = listOf("работа", "работе", "работу", "работаю", "работал").map { TextAnalyzer.stem(it) }.toSet()
        assertEquals(setOf("работ"), forms)
    }

    @Test fun synonymsFileWithBomIsParsed() {
        val bom = Char(0xFEFF).toString()
        val map = TextAnalyzer.parseSynonyms(listOf("${bom}что = шо, чо"))
        assertEquals(mapOf("шо" to "что", "чо" to "что"), map)
    }

    @Test fun typoTolerance() = assertAnswersAbout("посоветуй фильмм", "фильм")

    @Test fun humanOrBot() = assertAnswersAbout("а ты вообще человек или бот?", "ты бот", "ты человек")

    // То, что работало раньше, не должно сломаться
    // «прив», «хай», «салют»… — синонимы «привет» из synonyms.txt, поэтому тоже точное совпадение
    @Test fun shortGreetingStillExact() =
        assertAnswersAbout("привет", "привет", "прив", "хай", "салют", "здарова", "здрасте", "хелло")

    @Test fun punctuationDoesNotBreakExactMatch() = assertEquals(chosenQuestions("привет"), chosenQuestions("Привет!!!"))

    @Test fun lonelyStillWorks() = assertAnswersAbout("мне очень грустно и одиноко, никто не пишет", "одинок", "грустн")

    @Test fun movieAdviceStillWorks() = assertAnswersAbout("посоветуй какой фильм посмотреть вечером с девушкой", "фильм")

    @Test fun greetingDoesNotHijackQuestion() = assertAnswersAbout("привет, как дела?", "дела")

    /** Регрессия по всей базе: каждый обычный вопрос, отправленный как сообщение, находит себя точным совпадением. */
    @Test fun everyQuestionFindsItself() {
        val questions = File("src/main/assets/answer.txt").readLines(Charsets.UTF_8)
            .map { it.removePrefix(Char(0xFEFF).toString()).trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("|") }
            .map { it.substringBefore("|").trim() }
            .filter { !it.contains("*") && !it.startsWith("regex:", ignoreCase = true) }
            .distinct()
        val misses = questions.filter { q ->
            val results = db.searchAnswers(q, "")
            results.none { it.tier <= 1 && it.answer.questionText.equals(q, ignoreCase = true) }
        }
        assertTrue("Не нашли себя ${misses.size} из ${questions.size}: ${misses.take(10)}", misses.isEmpty())
    }
}
