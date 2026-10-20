package com.vkbot.manager.utils

import android.app.ActivityManager
import android.content.Context
import com.vkbot.manager.BotService

/**
 * Работает ли сервис бота на самом деле. Сохранённым флагам верить нельзя:
 * при обновлении приложения или убийстве процесса сервис не успевает их сбросить.
 */
object ServiceState {
    @Suppress("DEPRECATION") // Для своих сервисов getRunningServices по-прежнему работает
    fun isBotServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).orEmpty()
            .any { it.service.className == BotService::class.java.name && it.foreground }
    }
}
