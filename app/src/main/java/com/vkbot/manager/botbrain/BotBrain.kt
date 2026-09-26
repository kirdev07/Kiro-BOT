package com.vkbot.manager.botbrain

import android.content.Context
import android.util.Log
import com.vkbot.manager.utils.SettingsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * «Мозг» бота: выбор ответа из базы с учётом истории диалога и контекста.
 */
class BotBrain(val answerDatabase: AnswerDatabase?) {

    fun interface LogCallback {
        fun onLog(message: String)
    }

    private val userHistories = object : LinkedHashMap<String, UserResponseHistory>(MAX_USERS_HISTORY + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, UserResponseHistory>): Boolean {
            return size > MAX_USERS_HISTORY
        }
    }

    private val rwLock = ReentrantReadWriteLock()
    private var logCallback: LogCallback? = null

    constructor(context: Context?) : this(AnswerDatabase(context))

    private var fallbackResponses: List<String> = emptyList()

    /** Журнал «Не знаю ответа»: вопросы, на которые не нашлось ответа в базе. */
    val unanswered: UnansweredLog = answerDatabase?.fileManager?.loadUnanswered() ?: UnansweredLog()

    /** Вести ли журнал (переключатель в настройках; сервис подставляет SettingsManager). */
    var isUnansweredLogEnabled: () -> Boolean = { true }

    /** ИИ-помощник: вызывается, когда в базе нет ответа; null — ИИ не ответил (дальше запасной ответ). */
    var aiResponder: ((BotMessage) -> String?)? = null

    init {
        log("BotBrain инициализирован")
        loadFallbacks()
        // Пока бот был выключен, базу могли пополнить в редакторе
        answerDatabase?.let { db -> unanswered.prune { db.searchAnswers(it, "").isNotEmpty() } }
    }

    private fun loadFallbacks() {
        if (answerDatabase?.fileManager != null) {
            fallbackResponses = answerDatabase.fileManager.loadTxtList("fallback.txt")
        }
    }

    fun setLogCallback(callback: LogCallback?) {
        this.logCallback = callback
    }

    private fun log(message: String) {
        logCallback?.onLog(message) ?: Log.i(TAG, message)
    }

    fun processMessage(message: BotMessage?): BotResponse? {
        if (message == null || message.text.isBlank()) return null
        try {
            val sr = rwLock.read { findAnswerSmart(message) }
            if (sr != null) return prepareResponse(sr, message)

            // В базе ответа нет: спрашиваем ИИ (без блокировки — запрос может идти до минуты)
            val aiAnswer = aiResponder?.invoke(message)
            if (isUnansweredLogEnabled()) unanswered.record(message.text, suggestion = aiAnswer.orEmpty())
            if (aiAnswer != null) return BotResponse(aiAnswer)

            val fallback = getFallbackResponse() ?: return null
            return prepareResponse(SearchResult(AnswerElement(id = -1, answerText = fallback.text, answerAttachments = fallback.attachments)), message)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка процесса", e)
            return null
        }
    }

    private inline fun <T> ReentrantReadWriteLock.read(block: () -> T): T {
        readLock().lock()
        try { return block() } finally { readLock().unlock() }
    }

    private fun findAnswerSmart(message: BotMessage): SearchResult? {
        if (answerDatabase == null) return null

        val userId = message.authorId
        val history = getUserHistory(userId)
        val userContext = history.getContext()

        val candidates = answerDatabase.searchAnswers(message.text, userContext)
        if (candidates.isEmpty()) return null

        val freshCandidates = mutableListOf<SearchResult>()
        for (sr in candidates) {
            val candidate = sr.answer
            val limit = candidate.repetitionLimit
            if (limit > 0 && history.getRepetitionCount(candidate.id) >= limit) {
                continue
            }

            if (!history.wasResponseGiven(message.text, candidate.answerText)) {
                freshCandidates.add(sr)
            }
        }

        if (freshCandidates.isEmpty()) {
            for (sr in candidates) {
                val candidate = sr.answer
                val limit = candidate.repetitionLimit
                if (limit <= 0 || history.getRepetitionCount(candidate.id) < limit) {
                    freshCandidates.add(sr)
                }
            }
        }

        if (freshCandidates.isEmpty()) return null

        val chosen = selectionPool(freshCandidates).random()
        val chosenElement = chosen.answer

        answerDatabase.recordUsage(chosenElement)
        history.addResponse(chosenElement.id, message.text, chosenElement.answerText)
        history.setContext(chosenElement.resultContext)

        log("💬 Выбран ответ ID:${chosenElement.id} (Исп: ${chosenElement.usageCount})")
        return chosen
    }

    private fun getUserHistory(userId: String): UserResponseHistory {
        synchronized(userHistories) {
            var history = userHistories[userId]
            if (history == null) {
                history = UserResponseHistory()
                userHistories[userId] = history
            }
            return history
        }
    }

    private fun getFallbackResponse(): BotResponse? {
        try {
            if (SettingsManager.isFallbackSilenceEnabled) return null

            if (SettingsManager.isRandomFallbackEnabled && kotlin.random.Random.nextDouble() > 0.7 && answerDatabase != null) {
                val randomElement = answerDatabase.getRandomAnswer()
                if (randomElement != null) return BotResponse(randomElement.answerText, randomElement.answerAttachments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка настроек", e)
        }

        val list = fallbackResponses
        if (list.isEmpty()) {
            // Техническую причину — в лог, собеседнику — нейтральная фраза
            log("fallback.txt пуст или не найден — отвечаю стандартной фразой")
            return BotResponse("Извините, я не знаю, что ответить.", emptyList())
        }
        val text = list.random()
        return BotResponse(text, emptyList())
    }

    private fun prepareResponse(sr: SearchResult?, originalMessage: BotMessage): BotResponse? {
        if (sr?.answer == null) return null

        val element = sr.answer
        val responseText = replacePlaceholders(element.answerText, originalMessage, sr)

        return BotResponse.Builder()
            .text(responseText)
            .addAttachments(element.answerAttachments)
            .build()
    }

    private fun replacePlaceholders(text: String?, message: BotMessage, sr: SearchResult?): String {
        var result = text ?: ""
        val now = System.currentTimeMillis()
        val currentDate = Date(now)

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        if (sr != null && sr.hasGroups()) {
            val groups = sr.capturedGroups
            for (i in 1 until groups.size) {
                result = result.replace("{$i}", groups[i])
            }
        }

        result = result.replace("{name}", message.authorName.ifEmpty { "Пользователь" })
            .replace("{user_id}", message.authorId)
            .replace("{time}", timeFmt.format(currentDate))
            .replace("{date}", dateFmt.format(currentDate))
            .replace("{last_msg}", message.text)
            .replace("{random}", (0..100).random().toString())

        return result
    }

    /** Сохраняет на диск счётчики использования и журнал «Не знаю ответа», если они менялись. */
    fun saveStats() {
        answerDatabase?.saveUsageStats()
        if (unanswered.isDirty) answerDatabase?.fileManager?.saveUnanswered(unanswered)
    }

    fun reloadDatabase() {
        rwLock.writeLock().lock()
        try {
            var success = false
            if (answerDatabase != null) {
                success = answerDatabase.reloadFromFile()
                loadFallbacks()
                // Вопросы, на которые теперь есть ответ, из журнала убираем
                val pruned = unanswered.prune { answerDatabase.searchAnswers(it, "").isNotEmpty() }
                if (pruned > 0) log("Из журнала «Не знаю ответа» убрано: $pruned")
            }
            log("База данных перезагружена: " + if (success) "УСПЕХ" else "ОШИБКА")
        } finally {
            rwLock.writeLock().unlock()
        }
        saveStats()
    }

    /** Добавляет ответ в базу (команда владельца «!запомни»). */
    fun teach(question: String, answer: String): Boolean {
        val fm = answerDatabase?.fileManager ?: return false
        val saved = fm.saveAnswerDatabaseWithBackup(AndroidFileManager.withAnswer(fm.loadAnswerDatabase(), question, answer))
        if (saved) reloadDatabase()
        return saved
    }

    /** Удаляет все ответы на вопрос (команда владельца «!забудь»). @return сколько удалено, -1 — ошибка записи */
    fun forget(question: String): Int {
        val fm = answerDatabase?.fileManager ?: return -1
        val (kept, removed) = AndroidFileManager.withoutQuestion(fm.loadAnswerDatabase(), question)
        if (removed == 0) return 0
        if (!fm.saveAnswerDatabaseWithBackup(kept)) return -1
        reloadDatabase()
        return removed
    }

    /** Убирает запись из журнала (кнопка «Удалить из списка» в редакторе). */
    fun forgetUnanswered(text: String) {
        if (unanswered.remove(text)) saveStats()
    }

    fun clearUnanswered() {
        unanswered.clear()
        saveStats()
    }

    private class UserResponseHistory {
        private val questionToResponses = object : LinkedHashMap<String, MutableList<String>>(50 + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, MutableList<String>>): Boolean {
                return size > 50
            }
        }

        private val answerIdWeights = mutableMapOf<Long, Int>()
        private var currentContext = ""
        private var contextTimestamp: Long = 0

        @Synchronized
        fun addResponse(answerId: Long, question: String, response: String) {
            answerIdWeights[answerId] = getRepetitionCount(answerId) + 1

            val qKey = question.lowercase(Locale.getDefault()).trim()
            val list = questionToResponses.getOrPut(qKey) { mutableListOf() }

            list.add(response)
            if (list.size > MAX_HISTORY_SIZE) {
                list.removeAt(0)
            }
        }

        @Synchronized
        fun wasResponseGiven(question: String, response: String): Boolean {
            val responses = questionToResponses[question.lowercase(Locale.getDefault()).trim()]
            return responses?.contains(response) == true
        }

        @Synchronized
        fun getRepetitionCount(answerId: Long): Int {
            return answerIdWeights[answerId] ?: 0
        }

        @Synchronized
        fun getContext(): String {
            if (System.currentTimeMillis() - contextTimestamp > CONTEXT_TTL) currentContext = ""
            return currentContext
        }

        @Synchronized
        fun setContext(context: String?) {
            this.currentContext = context ?: ""
            this.contextTimestamp = System.currentTimeMillis()
        }

        companion object {
            private const val CONTEXT_TTL = 30 * 60 * 1000L
            private const val MAX_HISTORY_SIZE = 5
        }
    }

    companion object {
        private const val TAG = "BotBrain"
        private const val MAX_USERS_HISTORY = 1000
        /** Ответы с оценкой не ниже 90% от лучшей считаются равноценными. */
        internal const val NEAR_TIE = 0.9f
        /** Уровни 0–3 — точное совпадение и regex: все варианты равноправны (команда с 10 картинками). */
        private const val LAST_EXACT_TIER = 3

        /**
         * Из чего бот случайно выбирает ответ: лучший уровень совпадения и почти равные по оценке.
         * Для точных совпадений — все варианты, для совпадений по смыслу — не больше трёх лучших.
         */
        internal fun selectionPool(candidates: List<SearchResult>): List<SearchResult> {
            if (candidates.isEmpty()) return emptyList()
            val bestTier = candidates.minOf { it.tier }
            val best = candidates.filter { it.tier == bestTier }
            val topScore = best.maxOf { it.score }
            val pool = best.filter { it.score >= topScore * NEAR_TIE }
            return if (bestTier <= LAST_EXACT_TIER) pool else pool.take(3)
        }
    }
}
