package com.vkbot.manager.ai

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** POST/GET JSON для ИИ-сервисов без SDK (Gemini, YandexGPT, любой OpenAI-совместимый API). */
internal object AiHttp {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    class Response(val code: Int, val body: String) {
        val json: JSONObject? get() = runCatching { JSONObject(body) }.getOrNull()
    }

    /** @throws AiException при ошибке сети */
    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): Response = request(url, headers, body)

    /** @throws AiException при ошибке сети */
    fun getJson(url: String, headers: Map<String, String>): Response = request(url, headers, null)

    private fun request(url: String, headers: Map<String, String>, body: JSONObject?): Response {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("неверный адрес API: $url")
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            return Response(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } catch (e: UnknownHostException) {
            throw AiException("сервер ИИ недоступен (${URL(url).host})")
        } catch (e: SocketTimeoutException) {
            throw AiException("ИИ не ответил вовремя")
        } catch (e: IOException) {
            throw AiException("ошибка связи с ИИ: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    /** Текст ошибки из ответа сервиса ({"error":{"message":…}} и похожие) или общий по коду. */
    fun describeError(provider: String, response: Response): String {
        val json = response.json
        val detail = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        val base = when (response.code) {
            400 -> "$provider: неверный запрос"
            401, 403 -> "$provider: неверный API-ключ или нет доступа"
            404 -> "$provider: модель или адрес API не найдены"
            429 -> "$provider: превышен лимит запросов или закончились деньги на счёте"
            in 500..599 -> "$provider: сервис временно недоступен"
            else -> "$provider: ошибка (код ${response.code})"
        }
        return if (detail != null) "$base — ${detail.take(200)}" else base
    }
}
