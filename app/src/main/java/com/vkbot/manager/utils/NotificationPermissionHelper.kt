package com.vkbot.manager.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Помощник для работы с разрешениями уведомлений.
 * Оптимизирован под Android 13+ (API 33+) и minSdk 30.
 */
object NotificationPermissionHelper {
    
    const val REQUEST_CODE_NOTIFICATION_PERMISSION = 2001
    
    /**
     * Проверяет, разрешены ли уведомления.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        // 1. Проверяем глобальную настройку уведомлений
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        // 2. Для Android 13+ проверяем конкретное разрешение POST_NOTIFICATIONS
        return hasNotificationPermission(context)
    }

    /**
     * Проверяет, есть ли разрешение на отправку уведомлений.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Запрашивает разрешение на отправку уведомлений.
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
    }
    
    /**
     * Проверяет, нужно ли показать объяснение для разрешения.
     */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }
    
    /**
     * Открывает настройки приложения для ручного включения уведомлений.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Получает текст объяснения для пользователя.
     */
    fun getPermissionExplanation(): String {
        return """
            🔔 Разрешение на уведомления
            
            Для работы Kiro Bot в фоне нужно разрешение на уведомления:
            
            • Показ статуса работы бота
            • Уведомления о запуске и остановке
            • Отображение ошибок и предупреждений
            
            Без этого разрешения бот может работать нестабильно.
        """.trimIndent()
    }
}