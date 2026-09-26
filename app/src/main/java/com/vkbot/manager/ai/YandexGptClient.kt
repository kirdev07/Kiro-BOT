package com.vkbot.manager.ai

import org.json.JSONArray
import org.json.JSONObject

/** YandexGPT (Yandex AI Studio): API-ключ сервисного аккаунта + идентификатор каталога. */
class YandexGptClient(
    private val apiKey: String,
    private val folderId: String,
    private val model: String,
    private val endpoint: String = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
) : AiClient {

    override fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String {
        if (folderId.isBlank()) throw AiException("YandexGPT: не указан идентификатор каталога")
        val messages = JSONArray().put(JSONObject().put("role", "system").put("text", system))
        for (turn in history) messages.put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("text", turn.text))
        val body = JSONObject()
            .put("modelUri", modelUri())
            .put("completionOptions", JSONObject().put("stream", false).put("temperature", 0.6).put("maxTokens", maxTokens.toString()))
            .put("messages", messages)

        val response = AiHttp.postJson(endpoint, mapOf("Authorization" to "Api-Key $apiKey", "x-folder-id" to folderId), body)
        if (response.code !in 200..299) throw AiException(AiHttp.describeError("YandexGPT", response))

        val text = response.json?.optJSONObject("result")?.optJSONArray("alternatives")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("text").orEmpty().trim()
        if (text.isEmpty()) throw AiException("YandexGPT вернул пустой ответ")
        return text
    }

    /** «yandexgpt-lite/latest» → «gpt://<каталог>/yandexgpt-lite/latest»; полный gpt://… — как есть. */
    internal fun modelUri(): String = if (model.startsWith("gpt://")) model else "gpt://$folderId/${model.trim('/')}"
}
