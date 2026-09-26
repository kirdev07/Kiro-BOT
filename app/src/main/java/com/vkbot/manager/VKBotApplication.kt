package com.vkbot.manager

import android.app.Application
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.SettingsManager

/**
 * Основной класс приложения Kiro Bot.
 */
class VKBotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Инициализация ЧС для работы в фоновом режиме сервиса
        BlacklistManager.init(this)

        // Инициализация глобальных настроек
        SettingsManager.init(this)
    }
}
