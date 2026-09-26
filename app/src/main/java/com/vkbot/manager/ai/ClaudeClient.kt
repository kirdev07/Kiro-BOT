package com.vkbot.manager.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigAdaptive
import java.time.Duration

/**
 * Claude через официальный SDK Anthropic.
 * Для реплик в чате: адаптивное мышление с низким усилием (быстро и дёшево);
 * для Claude Opus 5 включены серверные запасные модели на случай отказа по соображениям безопасности.
 */
class ClaudeClient(
    apiKey: String,
    private val model: String,
    baseUrl: String? = null
) : AiClient {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(60))
        .maxRetries(1)
        .apply { if (!baseUrl.isNullOrBlank()) baseUrl(baseUrl) }
        .build()

    override fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String {
        val params = MessageCreateParams.builder()
            .model(model)
            // С адаптивным мышлением max_tokens включает и размышления — с запасом
            .maxTokens(maxOf(maxTokens * 4, 2048).toLong())
            .system(system)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .apply {
                for (turn in history) if (turn.fromUser) addUserMessage(turn.text) else addAssistantMessage(turn.text)
                if (model == OPUS_5) {
                    // Серверные запасные модели при отказе (stop_reason = refusal)
                    putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        val response = try {
            client.messages().create(params)
        } catch (e: AnthropicServiceException) {
            throw AiException(describe(e.statusCode()))
        } catch (e: AnthropicIoException) {
            throw AiException("нет связи с Claude: ${e.message}")
        }

        if (response.stopReason().orElse(null) == StopReason.REFUSAL) throw AiException("Claude отказался отвечать на это сообщение")
        val text = response.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }
            .joinToString("").trim()
        if (text.isEmpty()) throw AiException("Claude вернул пустой ответ")
        return text
    }

    companion object {
        const val OPUS_5 = "claude-opus-5"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        fun describe(status: Int): String = when (status) {
            401 -> "неверный API-ключ Claude"
            403 -> "у ключа нет доступа к этой модели"
            404 -> "модель не найдена — проверьте название"
            429 -> "превышен лимит запросов Claude, попробуйте позже"
            529, 503 -> "Claude сейчас перегружен"
            else -> "ошибка Claude (код $status)"
        }
    }
}
