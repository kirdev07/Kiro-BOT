package com.vkbot.manager.utils

import android.content.Context
import com.vkbot.manager.botbrain.AndroidFileManager

/**
 * Объект-утилита для хранения ответов бота на различные типы вложений (медиафайлы).
 * Вынесено из BotService для повышения читаемости кода.
 */
object MediaResponses {

    private var fileManager: AndroidFileManager? = null
    private val dynamicResponses = mutableMapOf<String, List<String>>()

    fun init(context: Context) {
        if (fileManager == null) {
            // applicationContext: объект живёт всё время процесса, контекст сервиса здесь утёк бы
            fileManager = AndroidFileManager(context.applicationContext)
        }
        loadAll()
    }

    private val mediaTypes = listOf(
        "audio_message", "audio", "doc", "graffiti", "video", 
        "photo", "sticker", "link", "wall", "video_message", "story"
    )

    fun loadAll() {
        val fm = fileManager ?: return
        for (type in mediaTypes) {
            dynamicResponses[type] = fm.loadTxtList("media_$type.txt")
        }
    }



    /**
     * Возвращает случайный ответ для заданного типа вложения.
     * Если тип не найден, возвращает null.
     */
    fun getRandomResponse(type: String): String? {
        val list = dynamicResponses[type]
        return if (!list.isNullOrEmpty()) {
            list.random()
        } else {
            null
        }
    }
}
