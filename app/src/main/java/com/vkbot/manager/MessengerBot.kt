package com.vkbot.manager

/**
 * Интерфейс ядра бота (сейчас — Telegram); сервис работает с ним, не зная деталей Bot API.
 *
 * Обработчик сообщений получает карту: text (без обращения к боту), raw_text (как написал пользователь),
 * from_id, peer_id, first_name, attachment_types (типы вложений: photo, video, audio_message, sticker, doc… — как в именах media_*.txt)
 * и возвращает text + attachments или null, если отвечать не нужно.
 */
interface MessengerBot {
    /** @return true, если бот запущен; false — ошибка токена/API (повтор бесполезен). */
    suspend fun start(): Boolean

    fun stop()

    fun clearUserCache()

    fun setMessageProcessor(processor: (Map<String, Any>) -> Map<String, Any>?)
}
