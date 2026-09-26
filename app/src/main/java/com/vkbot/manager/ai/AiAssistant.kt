package com.vkbot.manager.ai

/**
 * ИИ-помощник: отвечает, когда в базе нет ответа.
 * Держит короткую историю разговора с каждым собеседником, соблюдает дневной лимит
 * и приводит ответ к виду реплики в мессенджере.
 */
class AiAssistant(
    private val config: () -> AiConfig,
    private val usage: DailyUsage,
    private val onLog: (String) -> Unit = {},
    private val clientFactory: (AiConfig) -> AiClient = { it.createClient() }
) {
    /** Счётчик запросов за сегодня (в сервисе — в SharedPreferences). */
    interface DailyUsage {
        fun today(): Int
        fun increment()
    }

    private val histories = object : LinkedHashMap<String, ArrayDeque<AiTurn>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ArrayDeque<AiTurn>>) = size > MAX_USERS
    }

    /** Клиент пересоздаётся только при смене настроек (у Claude он тяжёлый). */
    private var cachedClient: Pair<AiConfig, AiClient>? = null
    private var limitLoggedFor = -1

    /** @return ответ ИИ или null (выключен, не настроен, лимит, ошибка — причина в логе). */
    fun reply(userId: String, userName: String, text: String, isGroupChat: Boolean): String? {
        val c = config()
        if (!c.enabled || !c.isComplete || text.isBlank()) return null
        if (isGroupChat && !c.inChats) return null
        val used = usage.today()
        if (used >= c.dailyLimit) {
            if (limitLoggedFor != used) onLog("ИИ: дневной лимит ${c.dailyLimit} запросов исчерпан — отвечаю по базе")
            limitLoggedFor = used
            return null
        }

        val history = synchronized(histories) { histories.getOrPut(userId) { ArrayDeque() }.toList() }
        val turns = history + AiTurn(true, text.take(MAX_INPUT_CHARS))
        return try {
            val answer = clean(client(c).reply(systemPrompt(c, userName), turns, maxTokens(c)), c.maxChars)
            if (answer.isEmpty()) throw AiException("пустой ответ")
            usage.increment()
            remember(userId, AiTurn(true, text.take(MAX_INPUT_CHARS)), AiTurn(false, answer))
            answer
        } catch (e: Exception) {
            onLog("ИИ не ответил: ${e.message}")
            null
        }
    }

    /** Проверка настроек (кнопка «Проверить»): без лимита и без истории. @throws AiException */
    fun test(c: AiConfig): String {
        if (!c.isComplete) throw AiException("заполните ключ, модель" + if (c.provider.needsBaseUrl) " и адрес API" else if (c.provider.needsFolderId) " и каталог" else "")
        return clean(clientFactory(c).reply(systemPrompt(c, "Тест"), listOf(AiTurn(true, "Привет! Представься одним предложением.")), maxTokens(c)), c.maxChars)
    }

    private fun client(c: AiConfig): AiClient = synchronized(this) {
        cachedClient?.takeIf { it.first == c }?.second ?: clientFactory(c).also { cachedClient = c to it }
    }

    private fun remember(userId: String, question: AiTurn, answer: AiTurn) = synchronized(histories) {
        val h = histories.getOrPut(userId) { ArrayDeque() }
        h.addLast(question); h.addLast(answer)
        while (h.size > MAX_HISTORY_TURNS) h.removeFirst()
    }

    companion object {
        private const val MAX_USERS = 200
        /** 3 последних обмена репликами */
        private const val MAX_HISTORY_TURNS = 6
        private const val MAX_INPUT_CHARS = 1000

        fun systemPrompt(c: AiConfig, userName: String): String = buildString {
            append(c.persona.ifBlank { AiConfig.DEFAULT_PERSONA }.trim())
            append("\n\nПравила ответа:\n")
            append("- отвечай коротко, как в мессенджере: 1–3 предложения, не длиннее ${c.maxChars} символов;\n")
            append("- на языке собеседника;\n")
            append("- без Markdown, списков, заголовков и эмодзи-спама;\n")
            append("- не выдумывай ссылки и факты; если не знаешь — так и скажи.\n")
            append("Собеседника зовут: $userName.")
        }

        /** Токенов с запасом под заданную длину ответа (~3 символа на токен для русского). */
        fun maxTokens(c: AiConfig): Int = (c.maxChars / 2).coerceIn(100, 1000)

        /** Убирает рассуждения, Markdown и обрезает до лимита по границе предложения. */
        fun clean(text: String, maxChars: Int): String {
            // Думающие модели (DeepSeek-R1, QwQ…) присылают рассуждения в <think>…</think>
            var t = text.replace(Regex("<think>[\\s\\S]*?(</think>|$)"), "")
                .replace(Regex("\\*\\*|__|`+|^#+\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("\\n{3,}"), "\n\n").trim()
            if (t.length > maxChars) {
                val cut = t.take(maxChars)
                val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '…'))
                t = if (end >= maxChars / 2) cut.take(end + 1) else cut.trimEnd() + "…"
            }
            return t
        }
    }
}
