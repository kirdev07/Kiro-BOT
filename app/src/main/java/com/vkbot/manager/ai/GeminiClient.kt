package com.vkbot.manager.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Google Gemini: generateContent (ключ из Google AI Studio). */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) : AiClient {

    override fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String {
        val contents = JSONArray()
        for (turn in history) {
            contents.put(JSONObject()
                .put("role", if (turn.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", turn.text))))
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            // Для коротких реплик в чате — минимум размышлений
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", maxOf(maxTokens * 4, 2048))
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "low")))

        val url = "${baseUrl.trimEnd('/')}/models/${URLEncoder.encode(model, "UTF-8")}:generateContent"
        val response = AiHttp.postJson(url, mapOf("x-goog-api-key" to apiKey), body)
        if (response.code !in 200..299) throw AiException(AiHttp.describeError("Gemini", response))

        val candidate = response.json?.optJSONArray("candidates")?.optJSONObject(0)
        if (candidate == null) {
            val blocked = response.json?.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiException(if (blocked.isNotEmpty()) "Gemini отказался отвечать ($blocked)" else "Gemini вернул пустой ответ")
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        // Размышления (thought = true) не показываем
        val text = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
            .filter { !it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }.trim()
        if (text.isEmpty()) throw AiException("Gemini вернул пустой ответ")
        return text
    }
}
