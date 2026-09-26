package com.vkbot.manager.ai

/** Реплика диалога для ИИ. */
data class AiTurn(val fromUser: Boolean, val text: String)

/** Ошибка ИИ с понятной причиной для лога и кнопки «Проверить». */
class AiException(message: String) : Exception(message)

/** Клиент одного поставщика ИИ. Вызывать не из главного потока (сетевые запросы). */
interface AiClient {
    /**
     * @param system инструкция (характер бота, правила ответа)
     * @param history предыдущие реплики с этим собеседником, старые — первыми; последняя — текущее сообщение
     * @throws AiException с причиной (неверный ключ, лимит, сеть…)
     */
    fun reply(system: String, history: List<AiTurn>, maxTokens: Int): String
}
