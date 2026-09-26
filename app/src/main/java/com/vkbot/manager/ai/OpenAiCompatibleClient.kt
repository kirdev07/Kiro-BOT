package com.vkbot.manager.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Любой сервис с OpenAI-совместимым API (/chat/completions): OpenAI, OpenRouter, DeepSeek, Groq,
 * Mistral, локальные Ollama / LM Studio и многие другие. Адрес, ключ и модель задаёт пользователь.
 */
class OpenAiCompatibleClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : AiClient {

    // Проверяется при запросе, чтобы ошибка адреса попала в лог, а не уронила создание клиента
    private val endpoint by lazy { endpointFor(baseUrl) }

    override fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (turn in history) messages.put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", turn.text))
        // Думающие модели (gpt-oss, R1…) тратят токены на рассуждения — иначе ответ придёт пустым.
        // Длину самого ответа держат промпт и AiAssistant.clean().
        val tokens = maxTokens.coerceAtLeast(MIN_OUTPUT_TOKENS)
        val body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", tokens)

        var response = AiHttp.postJson(endpoint, headers(), body)
        // Новые модели OpenAI принимают только max_completion_tokens
        if (response.code == 400 && response.body.contains("max_tokens") && response.body.contains("max_completion_tokens")) {
            body.remove("max_tokens")
            body.put("max_completion_tokens", tokens)
            response = AiHttp.postJson(endpoint, headers(), body)
        }
        if (response.code !in 200..299) throw AiException(AiHttp.describeError("ИИ", response))

        val message = response.json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw AiException("ИИ вернул ответ в непонятном формате")
        val text = when (val content = message.opt("content")) {
            is String -> content
            // Некоторые сервисы присылают content списком частей
            is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
            else -> ""
        }.trim()
        if (text.isEmpty()) throw AiException("ИИ вернул пустой ответ")
        return text
    }

    /** Ключ необязателен: локальным Ollama / LM Studio он не нужен. */
    private fun headers(): Map<String, String> = headersFor(apiKey)

    companion object {
        /** Адрес без схемы дополняется https://, путь /chat/completions — если его нет. */
        fun endpointFor(baseUrl: String): String {
            val url = baseUrl.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
            val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
            if (url.startsWith("http://", ignoreCase = true) && host !in LOCAL_HOSTS)
                throw AiException("адрес http:// разрешён только для localhost — укажите https://")
            return if (url.endsWith("/chat/completions")) url else "$url/chat/completions"
        }

        /**
         * Модели, доступные по ключу (GET /models), без распознавания речи, озвучки и фильтров.
         * @throws AiException
         */
        fun listModels(baseUrl: String, apiKey: String): List<String> {
            val url = endpointFor(baseUrl).removeSuffix("/chat/completions") + "/models"
            val response = AiHttp.getJson(url, headersFor(apiKey))
            if (response.code !in 200..299) throw AiException(AiHttp.describeError("ИИ", response))
            val data = response.json?.optJSONArray("data") ?: throw AiException("сервис не отдал список моделей")
            return (0 until data.length())
                .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                .filterNot { id -> NOT_CHAT.any { it in id.lowercase() } }
                .sorted()
        }

        private fun headersFor(apiKey: String): Map<String, String> =
            if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

        private const val MIN_OUTPUT_TOKENS = 1024
        private val NOT_CHAT = listOf("whisper", "tts", "orpheus", "guard", "embed", "dall-e", "moderation")

        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
    }
}
