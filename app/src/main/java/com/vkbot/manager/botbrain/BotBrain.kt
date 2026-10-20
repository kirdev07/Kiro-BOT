package com.vkbot.manager.botbrain

import android.content.Context
import android.util.Log
import com.vkbot.manager.botbrain.conversation.AnswerSelector
import com.vkbot.manager.botbrain.conversation.BrainDebug
import com.vkbot.manager.botbrain.conversation.ConversationContext
import com.vkbot.manager.botbrain.conversation.ConversationPipeline
import com.vkbot.manager.botbrain.conversation.ConversationProcessor
import com.vkbot.manager.botbrain.conversation.ConversationRules
import com.vkbot.manager.botbrain.conversation.DatabaseContextProcessor
import com.vkbot.manager.botbrain.conversation.DatabaseExactProcessor
import com.vkbot.manager.botbrain.conversation.DatabaseResponder
import com.vkbot.manager.botbrain.conversation.DatabaseSmartProcessor
import com.vkbot.manager.botbrain.conversation.DialogHistory
import com.vkbot.manager.botbrain.conversation.FollowUpProcessor
import com.vkbot.manager.botbrain.conversation.MirrorFollowUpProcessor
import com.vkbot.manager.botbrain.conversation.Mood
import com.vkbot.manager.botbrain.conversation.SentimentAnalyzer
import com.vkbot.manager.botbrain.conversation.Placeholders
import com.vkbot.manager.botbrain.conversation.Reply
import com.vkbot.manager.botbrain.conversation.ReplyKind
import com.vkbot.manager.botbrain.conversation.ResponsePostProcessor
import com.vkbot.manager.botbrain.conversation.SentimentProcessor
import com.vkbot.manager.botbrain.conversation.SessionFactsProcessor
import com.vkbot.manager.botbrain.conversation.functions.ChanceFunction
import com.vkbot.manager.botbrain.conversation.functions.ChoiceFunction
import com.vkbot.manager.botbrain.conversation.functions.DateFunction
import com.vkbot.manager.botbrain.conversation.functions.MathFunction
import com.vkbot.manager.botbrain.conversation.functions.RandomFunction
import com.vkbot.manager.botbrain.conversation.functions.TimeFunction
import com.vkbot.manager.botbrain.conversation.functions.WhenFunction
import com.vkbot.manager.utils.SettingsManager
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.random.Random

/**
 * «Мозг» бота — координатор разговора:
 * сообщение → разговорный конвейер (база, функции, продолжения, реакции) → ИИ → запасной ответ → итоговая обработка.
 * Правила разговора живут в пакете conversation, здесь — только порядок и история.
 */
class BotBrain(
    val answerDatabase: AnswerDatabase?,
    presetRules: ConversationRules? = null,
    private val random: Random = Random.Default,
    /** Для тестов: наборы характера (id → файл набора без слияния). */
    private val presetPacks: Map<String, ConversationRules> = emptyMap(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun interface LogCallback {
        fun onLog(message: String)
    }

    private val rwLock = ReentrantReadWriteLock()
    private var logCallback: LogCallback? = null

    constructor(context: Context?) : this(AnswerDatabase(context))

    private var fallbackResponses: List<String> = emptyList()

    /** Правила из conversation.txt; в тестах подставляются готовые. */
    private val rulesFromFile = presetRules == null
    @Volatile private var baseRules: ConversationRules = presetRules ?: ConversationRules.EMPTY
    /** Готовые правила для каждого характера: "" — обычный, остальные — поверх него. */
    @Volatile private var rulesByPersonality: Map<String, ConversationRules> = mergePacks(baseRules, presetPacks)

    /** История диалогов (отдельно по чатам и собеседникам). */
    val history = DialogHistory(clock = clock)

    private val responder = DatabaseResponder { answerDatabase?.recordUsage(it) }

    private val pipeline = ConversationPipeline(buildProcessors())

    /** Журнал «Не знаю ответа»: вопросы, на которые не нашлось ответа в базе. */
    val unanswered: UnansweredLog = answerDatabase?.fileManager?.loadUnanswered() ?: UnansweredLog()

    /** Вести ли журнал (переключатель в настройках; сервис подставляет SettingsManager). */
    var isUnansweredLogEnabled: () -> Boolean = { true }

    /**
     * ИИ-помощник: вызывается, когда в базе нет ответа; получает последние реплики разговора.
     * null — ИИ не ответил (дальше отложенный ответ базы или запасная фраза).
     */
    var aiResponder: ((BotMessage, List<DialogHistory.Turn>) -> String?)? = null

    /** ИИ включён и настроен — тогда неуверенные совпадения базы уступают ему. */
    var aiAvailable: () -> Boolean = { false }

    /** Характер фраз (id из [ConversationRules.PERSONALITIES]), если не включено настроение. */
    var personality: () -> String = { "" }

    /** Менять характер по настроению бота к собеседнику (похвала / ругань). */
    var autoMood: () -> Boolean = { false }

    /** Отладка мозга: почему выбран такой ответ (в лог разработчика, не собеседнику). */
    var debugListener: ((BrainDebug) -> Unit)? = null

    init {
        log("BotBrain инициализирован")
        loadFiles()
        // Пока бот был выключен, базу могли пополнить в редакторе
        answerDatabase?.let { db -> unanswered.prune { db.searchAnswers(it, "").isNotEmpty() } }
    }

    private fun buildProcessors(): List<ConversationProcessor> = listOf(
        DatabaseContextProcessor(responder),
        SessionFactsProcessor(),
        MirrorFollowUpProcessor(responder),
        DatabaseExactProcessor(responder),
        ChoiceFunction(), ChanceFunction(), WhenFunction(), TimeFunction(), DateFunction(), MathFunction(), RandomFunction(),
        FollowUpProcessor(),
        SentimentProcessor(),
        DatabaseSmartProcessor(responder)
    )

    private fun loadFiles() {
        val fm = answerDatabase?.fileManager ?: return
        fallbackResponses = fm.loadTxtList("fallback.txt")
        reloadRules()
    }

    /** Перечитывает conversation.txt и наборы характера. */
    fun reloadRules() {
        val fm = answerDatabase?.fileManager ?: return
        if (!rulesFromFile) return
        val base = ConversationRules.parse(fm.loadTxtList(ConversationRules.FILE_NAME))
        val packs = ConversationRules.PERSONALITIES.filter { it.isNotEmpty() }
            .associateWith { ConversationRules.parse(fm.loadTxtList(ConversationRules.personalityFile(it))) }
        rulesByPersonality = mergePacks(base, packs)
        baseRules = base
    }

    private fun mergePacks(base: ConversationRules, packs: Map<String, ConversationRules>): Map<String, ConversationRules> =
        mapOf("" to base) + packs.mapValues { (_, pack) -> base.overriddenBy(pack) }

    /** Характер для этого сообщения: по настроению или выбранный в настройках. */
    private fun personalityFor(dialog: DialogHistory.Dialog): String =
        if (autoMood()) Mood.personalityFor(dialog.mood()) else personality()

    fun setLogCallback(callback: LogCallback?) {
        this.logCallback = callback
    }

    private fun log(message: String) {
        logCallback?.onLog(message) ?: Log.i(TAG, message)
    }

    fun processMessage(message: BotMessage?): BotResponse? {
        if (message == null || message.text.isBlank()) return null
        try {
            val dialog = history.dialog(message.chatId, message.authorId)
            // Настроение меняется от похвалы и ругани — даже если ответит база
            dialog.changeMood(Mood.deltaFor(SentimentAnalyzer.classify(ConversationRules.words(message.text), baseRules)))
            val personality = personalityFor(dialog)
            val rules = rulesByPersonality[personality] ?: baseRules
            val ctx = ConversationContext(message, dialog, rules, random, clock(), aiAvailable() && aiResponder != null) { text, context ->
                answerDatabase?.searchAnswers(text, context).orEmpty()
            }
            var reason = ""
            val raw = rwLock.read { pipeline.run(ctx) } ?: run {
                reason = "в базе нет ответа"
                answerUnknown(ctx).also { if (it?.kind == ReplyKind.FALLBACK) reason += ", ИИ не ответил" }
            } ?: return null
            val reply = ResponsePostProcessor.process(raw, ctx)
            if (reply.text.isEmpty() && reply.attachments.isEmpty()) return null

            dialog.addTurn(message.text, reply.text, reply.processor)
            if (reply.result != null) log("💬 Выбран ответ ID:${reply.result.answer.id} (Исп: ${reply.result.answer.usageCount})")
            debugListener?.invoke(debugOf(ctx, reply, reason).copy(mood = if (autoMood()) Mood.describe(dialog.mood()) else "", personality = personality))
            return BotResponse(reply.text, reply.attachments)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка процесса", e)
            return null
        }
    }

    /** Ответа нет ни в базе, ни у разговорных обработчиков: журнал, ИИ, запасная фраза. */
    private fun answerUnknown(ctx: ConversationContext): Reply? {
        // Без блокировки базы — запрос к ИИ может идти до минуты
        val aiAnswer = aiResponder?.invoke(ctx.message, ctx.dialog.recentTurns(AI_HISTORY_TURNS))
        if (aiAnswer != null) {
            if (isUnansweredLogEnabled()) unanswered.record(ctx.message.text, suggestion = aiAnswer)
            return Reply(aiAnswer, ReplyKind.AI, "ИИ")
        }
        // ИИ не ответил — неуверенный ответ базы лучше запасной фразы
        if (ctx.deferred.isNotEmpty()) {
            rwLock.read { responder.answer(ctx, ctx.deferred, "база: по смыслу (ИИ не ответил)") }?.let { return it }
        }
        if (isUnansweredLogEnabled()) unanswered.record(ctx.message.text)
        val fallback = getFallbackResponse() ?: return null
        return Reply(Placeholders.apply(fallback.text, ctx), ReplyKind.FALLBACK, "запасной ответ", fallback.attachments)
    }

    private fun debugOf(ctx: ConversationContext, reply: Reply, reason: String) = BrainDebug(
        message = ctx.text,
        processor = reply.processor,
        kind = reply.kind,
        tier = reply.result?.tier,
        score = reply.result?.score,
        context = ctx.context,
        candidates = AnswerSelector.selectionPool(ctx.search).take(3).ifEmpty { ctx.search.take(3) },
        tried = ctx.tried.toList(),
        reason = reason
    )

    private inline fun <T> ReentrantReadWriteLock.read(block: () -> T): T {
        readLock().lock()
        try { return block() } finally { readLock().unlock() }
    }

    private fun getFallbackResponse(): BotResponse? {
        try {
            if (SettingsManager.isFallbackSilenceEnabled) return null

            if (SettingsManager.isRandomFallbackEnabled && random.nextDouble() > 0.7 && answerDatabase != null) {
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
        return BotResponse(list.random(random), emptyList())
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
                loadFiles()
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

    companion object {
        private const val TAG = "BotBrain"
        /** Сколько последних обменов репликами видит ИИ. */
        private const val AI_HISTORY_TURNS = 3
    }
}
