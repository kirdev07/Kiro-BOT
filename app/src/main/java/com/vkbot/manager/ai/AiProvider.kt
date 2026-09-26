package com.vkbot.manager.ai

/** Поставщики ИИ. Модели в [models] — подсказки; можно вписать любую другую. */
enum class AiProvider(
    val id: String,
    val title: String,
    val models: List<String>,
    val needsBaseUrl: Boolean = false,
    val needsFolderId: Boolean = false,
    val keyOptional: Boolean = false
) {
    CLAUDE("claude", "Claude (Anthropic)", listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5")),
    GEMINI("gemini", "Gemini (Google)", listOf("gemini-3.8-flash", "gemini-3.8-flash-lite")),
    YANDEX("yandex", "YandexGPT", listOf("yandexgpt-lite/latest", "yandexgpt/latest"), needsFolderId = true),
    /** Любой сервис с OpenAI-совместимым API: адрес задаёт пользователь, ключ необязателен (локальные модели). */
    OPENAI_COMPATIBLE("openai", "Любой API (OpenAI-совместимый)", emptyList(), needsBaseUrl = true, keyOptional = true);

    val defaultModel: String get() = models.firstOrNull().orEmpty()

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: CLAUDE

        /** Адреса популярных сервисов для «Любого API». */
        val BASE_URL_PRESETS = listOf(
            "https://api.openai.com/v1",
            "https://openrouter.ai/api/v1",
            "https://api.deepseek.com",
            "https://api.groq.com/openai/v1",
            "https://api.x.ai/v1",
            "https://api.mistral.ai/v1",
            "http://localhost:11434/v1",
            "http://localhost:1234/v1"
        )
    }
}

/** Настройки ИИ-помощника. */
data class AiConfig(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.CLAUDE,
    val model: String = AiProvider.CLAUDE.defaultModel,
    val apiKey: String = "",
    val baseUrl: String = "",
    val folderId: String = "",
    val persona: String = DEFAULT_PERSONA,
    val maxChars: Int = 300,
    val dailyLimit: Int = 200,
    val inChats: Boolean = false
) {
    /** Всё нужное для запроса заполнено. */
    val isComplete: Boolean
        get() = model.isNotBlank() &&
            (apiKey.isNotBlank() || provider.keyOptional) &&
            (!provider.needsBaseUrl || baseUrl.isNotBlank()) &&
            (!provider.needsFolderId || folderId.isNotBlank())

    companion object {
        const val DEFAULT_PERSONA =
            "Ты — Kiro, дружелюбный бот-собеседник. Общаешься неформально, с лёгким юмором, как живой человек в мессенджере."
    }
}

/** Клиент под выбранного поставщика. */
fun AiConfig.createClient(): AiClient = when (provider) {
    AiProvider.CLAUDE -> ClaudeClient(apiKey, model)
    AiProvider.GEMINI -> GeminiClient(apiKey, model)
    AiProvider.YANDEX -> YandexGptClient(apiKey, folderId, model)
    AiProvider.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(baseUrl, apiKey, model)
}
