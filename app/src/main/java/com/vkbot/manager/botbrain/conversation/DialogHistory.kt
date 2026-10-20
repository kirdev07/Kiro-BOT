package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.TextAnalyzer

/**
 * История диалогов: отдельно для каждого собеседника в каждом чате (в беседе у каждого своя).
 * Хранит пары «реплика → ответ бота» со временем, контекст ответа из базы,
 * счётчики повторов и временные факты (имя). Число диалогов ограничено (LRU), реплики устаревают (TTL).
 */
class DialogHistory(
    private val maxDialogs: Int = MAX_DIALOGS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dialogs = object : LinkedHashMap<String, Dialog>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Dialog>) = size > maxDialogs
    }

    /** Диалог собеседника [userId] в чате [chatId] (для личных сообщений chatId можно не указывать). */
    fun dialog(chatId: String, userId: String): Dialog = synchronized(dialogs) {
        dialogs.getOrPut(keyOf(chatId, userId)) { Dialog(clock) }
    }

    val size: Int get() = synchronized(dialogs) { dialogs.size }

    /** Одна реплика собеседника и ответ бота на неё. */
    data class Turn(val userText: String, val botText: String, val time: Long, val source: String)

    class Dialog internal constructor(private val clock: () -> Long) {
        private val turns = ArrayDeque<Turn>()
        private val repetitions = HashMap<Long, Int>()
        private val facts = HashMap<String, Pair<String, Long>>()
        private var context = ""
        private var contextTime = 0L
        private var mood = 0.0
        private var moodTime = 0L

        @Synchronized
        fun addTurn(userText: String, botText: String, source: String) {
            turns.addLast(Turn(userText, botText, clock(), source))
            while (turns.size > MAX_TURNS) turns.removeFirst()
        }

        /** Последние [n] реплик не старше [HISTORY_TTL], от старых к новым. */
        @Synchronized
        fun recentTurns(n: Int = MAX_TURNS): List<Turn> {
            val from = clock() - HISTORY_TTL
            return turns.filter { it.time >= from }.takeLast(n)
        }

        /** Последняя реплика, если она свежее [maxAgeMs] — старую для продолжения разговора не используем. */
        @Synchronized
        fun lastTurn(maxAgeMs: Long = HISTORY_TTL): Turn? = turns.lastOrNull()?.takeIf { clock() - it.time <= maxAgeMs }

        fun lastUserMessage(maxAgeMs: Long = HISTORY_TTL): String? = lastTurn(maxAgeMs)?.userText

        fun lastBotResponse(maxAgeMs: Long = HISTORY_TTL): String? = lastTurn(maxAgeMs)?.botText

        /**
         * Бот недавно уже отвечал этим текстом (последние [COOLDOWN_TURNS] ответов или за [COOLDOWN_MS]).
         * Регистр, знаки и смайлы не важны: «Ясно.» и «ясно!» — повтор.
         */
        @Synchronized
        fun recentlySaid(text: String): Boolean {
            val from = clock() - COOLDOWN_MS
            val key = sameTextKey(text)
            return turns.withIndex().any { (i, t) -> sameTextKey(t.botText) == key && (i >= turns.size - COOLDOWN_TURNS || t.time >= from) }
        }

        /** Сколько ходов назад бот говорил этот текст (0 — последним ответом), null — давно или никогда. */
        @Synchronized
        fun turnsSinceSaid(text: String): Int? {
            val key = sameTextKey(text)
            val i = turns.indexOfLast { sameTextKey(it.botText) == key }
            return if (i < 0) null else turns.size - 1 - i
        }

        /** Контекст ответа из базы («результирующий контекст»): живёт [CONTEXT_TTL]. */
        @Synchronized
        fun context(): String {
            if (clock() - contextTime > CONTEXT_TTL) context = ""
            return context
        }

        @Synchronized
        fun setContext(value: String) {
            context = value
            contextTime = clock()
        }

        /**
         * Настроение бота к собеседнику: от −1 (обижен) до 1 (очень доволен).
         * Само возвращается к нулю: половина за [MOOD_HALF_LIFE].
         */
        @Synchronized
        fun mood(): Double {
            val elapsed = (clock() - moodTime).coerceAtLeast(0)
            return mood * Math.pow(0.5, elapsed.toDouble() / MOOD_HALF_LIFE)
        }

        @Synchronized
        fun changeMood(delta: Double) {
            mood = (mood() + delta).coerceIn(-1.0, 1.0)
            moodTime = clock()
        }

        @Synchronized
        fun repetitionCount(answerId: Long): Int = repetitions[answerId] ?: 0

        @Synchronized
        fun countRepetition(answerId: Long) {
            repetitions[answerId] = repetitionCount(answerId) + 1
        }

        /** Временный факт диалога (имя и т.п.), живёт [FACT_TTL]. */
        @Synchronized
        fun fact(key: String): String? {
            val (value, time) = facts[key] ?: return null
            if (clock() - time > FACT_TTL) { facts.remove(key); return null }
            return value
        }

        @Synchronized
        fun setFact(key: String, value: String) { facts[key] = value to clock() }

        @Synchronized
        fun clearFacts() = facts.clear()
    }

    companion object {
        const val MAX_DIALOGS = 1000
        /** Сколько реплик помнить в каждом диалоге. */
        const val MAX_TURNS = 20
        /** Реплики старше этого для продолжения разговора не используются. */
        const val HISTORY_TTL = 30 * 60 * 1000L
        /** Контекст из базы (requiredContext/resultContext). */
        const val CONTEXT_TTL = 30 * 60 * 1000L
        /** Короткое продолжение («да», «а ты?») относится только к совсем свежей реплике. */
        const val FOLLOW_UP_TTL = 10 * 60 * 1000L
        /** Ответ не повторяется в течение стольких ходов… */
        const val COOLDOWN_TURNS = 5
        /** …или стольких миллисекунд, пока есть другие варианты. */
        const val COOLDOWN_MS = 10 * 60 * 1000L
        /** Настроение бота остывает вдвое за 20 минут. */
        const val MOOD_HALF_LIFE = 20 * 60 * 1000L
        /** Факты диалога (имя) забываются через сутки. */
        const val FACT_TTL = 24 * 60 * 60 * 1000L

        /** Текст без регистра, знаков и смайлов — для сравнения ответов на повтор. */
        internal fun sameTextKey(text: String): String = TextAnalyzer.tokens(text, emptyMap()).joinToString(" ").ifEmpty { text }

        /** Ключ диалога: чат + собеседник. В личке chatId пустой или равен userId. */
        fun keyOf(chatId: String, userId: String): String = "${chatId.ifEmpty { userId }}|$userId"
    }
}
