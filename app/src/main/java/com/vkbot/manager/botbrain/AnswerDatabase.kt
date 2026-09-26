package com.vkbot.manager.botbrain

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * База ответов с индексами для поиска.
 *
 * Уровни поиска (чем меньше tier, тем приоритетнее):
 * 0–1 точное совпадение фразы, 2–3 regex/маски, 4–5 совпадение по смыслу слов,
 * 6–7 слабое совпадение (используется, только если лучших нет).
 * Чётный уровень — ответы с совпавшим контекстом диалога.
 */
class AnswerDatabase private constructor(
    val fileManager: AndroidFileManager?,
    presetAnswers: List<AnswerElement>?,
    private val presetSynonyms: Map<String, String>?
) {

    constructor(context: Context?) : this(context?.let { AndroidFileManager(it) }, null, null)

    /** Для unit-тестов: база из готового списка, без файлов. */
    internal constructor(answers: List<AnswerElement>, synonyms: Map<String, String> = emptyMap()) :
        this(null, answers, synonyms)

    /** Разобранный вопрос: основы слов и их суммарный вес. */
    private class QuestionInfo(val stems: List<String>, val weight: Double)

    /** Часть сообщения: основы слов, вес и доля от веса всего сообщения. */
    private class Segment(val stems: Set<String>, val weight: Double, val share: Double)

    private val answers = mutableMapOf<Long, AnswerElement>()

    // Индексы для быстрого поиска
    private val exactMatchIndex = mutableMapOf<String, MutableList<AnswerElement>>()
    private val keywordIndex = mutableMapOf<String, MutableList<AnswerElement>>()
    private val questionInfo = mutableMapOf<Long, QuestionInfo>()

    /** Вес слова: редкие в базе слова («питон») важнее частых («как», «ты»). */
    private var idf = mapOf<String, Double>()
    private var unknownWordIdf = 1.0
    private var synonyms = mapOf<String, String>()

    // Кэш значений для быстрого getRandomAnswer
    private var cachedValueList = listOf<AnswerElement>()

    // Индекс для регулярных выражений
    private val regexIndex = mutableMapOf<AnswerElement, Pattern>()

    private val usageDirty = AtomicBoolean(false)

    init {
        loadFromFile(presetAnswers)
    }

    /**
     * Загрузка базы данных с построением индексов.
     */
    private fun loadFromFile(presetAnswers: List<AnswerElement>? = null) {
        val startTime = System.currentTimeMillis()

        val loadedAnswers = presetAnswers ?: fileManager?.loadAnswerDatabase() ?: emptyList()
        val loadedSynonyms = presetSynonyms
            ?: fileManager?.let { TextAnalyzer.parseSynonyms(it.loadTxtList(SYNONYMS_FILE_NAME)) }
            ?: emptyMap()

        synchronized(answers) {
            answers.clear()
            exactMatchIndex.clear()
            keywordIndex.clear()
            questionInfo.clear()
            regexIndex.clear()
            synonyms = loadedSynonyms

            val questionStems = mutableMapOf<Long, List<String>>()
            for (answer in loadedAnswers) {
                answers[answer.id] = answer
                buildIndexesForAnswer(answer)?.let { questionStems[answer.id] = it }
            }
            buildWeights(questionStems)
            cachedValueList = answers.values.toList()
        }

        val loadTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "⚡ База данных загружена (${answers.size} эл., синонимов: ${synonyms.size}) за ${loadTime}мс")
    }

    /** @return основы слов вопроса или null для regex/масок. */
    private fun buildIndexesForAnswer(answer: AnswerElement): List<String>? {
        val questionLower = answer.questionText.lowercase(Locale.getDefault()).trim()

        // Поддержка масок со звездочкой
        if (questionLower.contains("*") && !questionLower.startsWith("regex:")) {
            val patternStr = questionLower.split("*").joinToString("(.*)") { Pattern.quote(it) }

            try {
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                regexIndex[answer] = pattern
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка компиляции маски: $questionLower", e)
            }
            return null
        }

        // Проверка на Regex
        if (questionLower.startsWith("regex:")) {
            try {
                // Берём исходный текст: lowercase ломает \S, \D, \W и т.п.
                val patternStr = answer.questionText.trim().substring(6).trim()
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                regexIndex[answer] = pattern
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка компиляции regex: $questionLower", e)
            }
            return null
        }

        val tokens = TextAnalyzer.tokens(answer.questionText, synonyms)
        if (tokens.isEmpty()) return null

        // Индекс точного совпадения: без знаков препинания, ё = е, с учётом синонимов
        exactMatchIndex.getOrPut(tokens.joinToString(" ")) { mutableListOf() }.add(answer)

        // Индекс по основам слов
        val stems = tokens.map { TextAnalyzer.stem(it) }.distinct()
        for (stem in stems) {
            keywordIndex.getOrPut(stem) { mutableListOf() }.add(answer)
        }
        return stems
    }

    private fun buildWeights(questionStems: Map<Long, List<String>>) {
        val n = questionStems.size.toDouble()
        // Квадрат IDF: иначе веса слишком плоские и набор частых слов («у меня сегодня») перевешивает редкое «начальник»
        fun weight(df: Int): Double { val v = ln((n + 1) / (df + 1)) + 1.0; return v * v }
        idf = keywordIndex.mapValues { (_, list) -> weight(list.size) }
        unknownWordIdf = weight(0)
        for ((id, stems) in questionStems) {
            questionInfo[id] = QuestionInfo(stems, stems.sumOf { weightOf(it) })
        }
    }

    /** Вес слова вопроса: служебные слова не несут смысла. */
    private fun weightOf(stem: String): Double =
        if (stem in STOP_WORDS) 0.0 else idf[stem] ?: unknownWordIdf

    /** Вес слова сообщения: незнакомые базе слова сопоставить не с чем — они не учитываются. */
    private fun messageWeightOf(stem: String): Double =
        if (stem in STOP_WORDS) 0.0 else idf[stem] ?: 0.0

    /**
     * Интеллектуальный поиск ответов с учетом контекста и Regex.
     */
    fun searchAnswers(query: String?, userContext: String?): List<SearchResult> {
        if (query.isNullOrBlank()) {
            return emptyList()
        }

        val userCtx = userContext ?: ""
        val tokens = TextAnalyzer.tokens(query, synonyms)

        val exactContextual = mutableListOf<SearchResult>()
        val exactGeneral = mutableListOf<SearchResult>()
        val regexContextual = mutableListOf<SearchResult>()
        val regexGeneral = mutableListOf<SearchResult>()
        val smartContextual = mutableListOf<SearchResult>()
        val smartGeneral = mutableListOf<SearchResult>()
        val weakContextual = mutableListOf<SearchResult>()
        val weakGeneral = mutableListOf<SearchResult>()

        synchronized(answers) {
            // 1. Точное совпадение фразы
            exactMatchIndex[tokens.joinToString(" ")]?.forEach { e ->
                addByContext(SearchResult(e), userCtx, exactContextual, exactGeneral)
            }

            // 2. Regex и маски
            for ((e, pattern) in regexIndex) {
                val matcher = pattern.matcher(query)
                if (matcher.find()) {
                    val groups = (0..matcher.groupCount()).map { matcher.group(it) ?: "" }
                    addByContext(SearchResult(e, groups), userCtx, regexContextual, regexGeneral)
                }
            }

            // 3. Совпадение по смыслу слов
            val alreadyFound = (exactContextual + exactGeneral + regexContextual + regexGeneral)
                .mapTo(HashSet()) { it.answer.id }
            val segments = buildSegments(query)
            val allStems = segments.first().stems

            val candidates = LinkedHashSet<AnswerElement>()
            for (stem in allStems) {
                if (stem !in STOP_WORDS) keywordIndex[stem]?.let { candidates.addAll(it) }
            }
            // Маленькая база: полный перебор, чтобы находить слова с опечатками
            if (candidates.isEmpty() && exactGeneral.isEmpty() && answers.size < 2000) {
                candidates.addAll(answers.values)
            }

            for (element in candidates) {
                if (element.id in alreadyFound) continue
                val info = questionInfo[element.id] ?: continue
                val strong = segments.maxOf { scoreSegment(info, it, MIN_QUESTION_COVERAGE) }
                if (strong > 0) {
                    addByContext(SearchResult(element, score = strong.toFloat()), userCtx, smartContextual, smartGeneral)
                } else {
                    val weak = segments.maxOf { scoreSegment(info, it, MIN_WEAK_QUESTION_COVERAGE) }
                    if (weak > 0) addByContext(SearchResult(element, score = weak.toFloat()), userCtx, weakContextual, weakGeneral)
                }
            }
        }

        val byUsage = compareByDescending<SearchResult> { it.answer.usageCount }
        val byScore = compareByDescending<SearchResult> { it.score }.then(byUsage)
        val tiers = listOf(
            exactContextual.sortedWith(byUsage), exactGeneral.sortedWith(byUsage),
            regexContextual.sortedWith(byUsage), regexGeneral.sortedWith(byUsage),
            smartContextual.sortedWith(byScore), smartGeneral.sortedWith(byScore),
            weakContextual.sortedWith(byScore), weakGeneral.sortedWith(byScore)
        )
        return tiers.flatMapIndexed { tier, list -> list.map { it.copy(tier = tier) } }
    }

    private fun addByContext(sr: SearchResult, userCtx: String, contextual: MutableList<SearchResult>, general: MutableList<SearchResult>) {
        val required = sr.answer.requiredContext
        when {
            required.isEmpty() -> general.add(sr)
            required.equals(userCtx, ignoreCase = true) -> contextual.add(sr)
        }
    }

    /** Всё сообщение + его части (для длинных сообщений из нескольких фраз). */
    private fun buildSegments(query: String): List<Segment> {
        fun stemsOf(text: String) = TextAnalyzer.tokens(text, synonyms).map { TextAnalyzer.stem(it) }.toSet()

        val wholeStems = stemsOf(query)
        val wholeWeight = wholeStems.sumOf { messageWeightOf(it) }.coerceAtLeast(1e-9)
        val whole = Segment(wholeStems, wholeWeight, 1.0)

        val clauses = TextAnalyzer.clauses(query)
        if (clauses.size < 2) return listOf(whole)
        // Части из одного слова («привет», «слушай») — обращения; их учитываем только в составе всего сообщения
        return listOf(whole) + clauses.map { stemsOf(it) }.filter { it.size >= 2 }.map { stems ->
            val weight = stems.sumOf { messageWeightOf(it) }.coerceAtLeast(1e-9)
            Segment(stems, weight, weight / wholeWeight)
        }
    }

    /**
     * Оценка 0..1: насколько вопрос из базы покрыт частью сообщения (qCoverage)
     * и какую долю смысла этой части он объясняет (mCoverage).
     * Благодаря второму множителю «привет» не перебивает длинный рассказ о работе.
     */
    private fun scoreSegment(info: QuestionInfo, segment: Segment, minQuestionCoverage: Double): Double {
        if (segment.stems.isEmpty() || info.weight <= 0.0) return 0.0
        var matchedQuestionWeight = 0.0
        for (qs in info.stems) {
            val found = qs in segment.stems || segment.stems.any { isFuzzyWordMatch(qs, it) }
            if (found) matchedQuestionWeight += weightOf(qs)
        }
        val qCoverage = matchedQuestionWeight / info.weight
        if (qCoverage < minQuestionCoverage) return 0.0

        // Числитель — вес совпавших слов вопроса: так учитываются и слова сообщения с опечатками
        val mCoverage = (matchedQuestionWeight / segment.weight).coerceAtMost(1.0)
        if (segment.stems.size > SHORT_MESSAGE_WORDS && mCoverage < MIN_MESSAGE_COVERAGE) return 0.0

        var score = qCoverage * sqrt(mCoverage) * sqrt(segment.share)

        // «Какой сегодня день» (вопрос) не ответ на «сегодня был ужасный день» (рассказ)
        if (info.stems.any { it in QUESTION_WORDS } && segment.stems.none { it in QUESTION_WORDS }) {
            score *= MISMATCH_PENALTY
        }
        // Приветствие не должно перебивать суть: «привет, как дела?» — ответ про дела
        if (info.stems.filter { it !in STOP_WORDS }.all { it in GREETINGS } &&
            segment.stems.count { it !in GREETINGS && messageWeightOf(it) > 0 } >= 2) {
            score *= MISMATCH_PENALTY
        }
        return score
    }

    val answersCount: Int
        get() {
            synchronized(answers) {
                return answers.size
            }
        }

    fun getRandomAnswer(): AnswerElement? {
        synchronized(answers) {
            if (cachedValueList.isEmpty()) return null
            return cachedValueList.randomOrNull()
        }
    }

    /** Учитывает использование ответа; на диск счётчики пишет [saveUsageStats]. */
    fun recordUsage(element: AnswerElement) {
        element.incrementUsageCount()
        usageDirty.set(true)
    }

    /** Сохраняет счётчики, если они менялись. Вызывать не из главного потока. */
    fun saveUsageStats() {
        val fm = fileManager ?: return
        if (!usageDirty.getAndSet(false)) return
        val snapshot = synchronized(answers) { answers.values.toList() }
        if (!fm.saveUsageStats(snapshot)) usageDirty.set(true)
    }

    fun reloadFromFile(): Boolean {
        return try {
            // Иначе накопленные в памяти счётчики пропадут вместе со старыми элементами
            saveUsageStats()
            loadFromFile()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка перезагрузки базы данных", e)
            false
        }
    }

    companion object {
        private const val TAG = "AnswerDatabase"
        private const val SYNONYMS_FILE_NAME = "synonyms.txt"

        /** Минимальная доля смысла вопроса из базы, найденная в сообщении. */
        private const val MIN_QUESTION_COVERAGE = 0.7
        /** Слабое совпадение (другой глагол: «сделать» вместо «оформить») — только если сильных нет. */
        private const val MIN_WEAK_QUESTION_COVERAGE = 0.5

        /** Служебные слова (союзы, частицы, предлоги) и усилители — вес 0. Хранятся в виде основ. */
        private val STOP_WORDS = listOf(
            "а", "и", "но", "или", "да", "же", "ли", "бы", "ну", "вот", "то",
            "в", "во", "на", "с", "со", "к", "ко", "у", "о", "об", "от", "до", "по", "за", "из", "для", "про", "при",
            "очень", "так", "просто", "вообще", "прям", "прямо", "еще", "уже", "тоже", "типа", "короче", "слушай"
        ).map { TextAnalyzer.stem(it) }.toSet()

        /** Вопросительные слова: вопрос из базы с ними не подходит к сообщению-утверждению. */
        private val QUESTION_WORDS = listOf(
            "как", "какой", "какая", "какое", "какие", "что", "где", "когда", "куда", "откуда",
            "почему", "зачем", "сколько", "кто", "чей", "ли"
        ).map { TextAnalyzer.stem(it) }.toSet()

        private val GREETINGS = listOf("привет", "здравствуй", "здравствуйте", "ку", "йоу")
            .map { TextAnalyzer.stem(it) }.toSet()

        /** Во сколько раз ослабляется оценка при несовпадении типа фразы. */
        private const val MISMATCH_PENALTY = 0.5
        /** Минимальная доля смысла сообщения, которую объясняет вопрос (для сообщений длиннее 3 слов). */
        private const val MIN_MESSAGE_COVERAGE = 0.1
        private const val SHORT_MESSAGE_WORDS = 3

        /** Опечатка в основе слова: первая буква совпадает, 1 ошибка (2 — для длинных слов). */
        private fun isFuzzyWordMatch(w1: String, w2: String): Boolean {
            if (w1.length < 5 || w2.length < 5 || w1[0] != w2[0]) return false
            val maxDist = if (min(w1.length, w2.length) >= 8) 2 else 1
            if (kotlin.math.abs(w1.length - w2.length) > maxDist) return false
            return calculateLevenshteinDistance(w1, w2) <= maxDist
        }

        private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length
            val d = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) d[i][0] = i
            for (j in 0..n) d[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                }
            }
            return d[m][n]
        }
    }
}
