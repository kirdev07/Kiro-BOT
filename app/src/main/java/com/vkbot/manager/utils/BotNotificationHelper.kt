package com.vkbot.manager.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vkbot.manager.BotService
import com.vkbot.manager.MainActivity
import com.vkbot.manager.R

/**
 * Централизованный хелпер для уведомлений бота.
 * Использует NotificationChannel (API 26+), совместим с Android 9–11.
 * Все PendingIntent используют FLAG_IMMUTABLE для Android 12+.
 */
object BotNotificationHelper {

    const val CHANNEL_ID = "VK_BOT_CHANNEL_V3"
    private const val CHANNEL_NAME = "Статус бота"
    private const val CHANNEL_DESC = "Статус работы бота"

    /**
     * Создаёт и регистрирует канал уведомлений.
     * NotificationChannel обязателен с API 26, minSdk 28 гарантирует наличие.
     */
    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Создаёт foreground-уведомление для сервиса бота.
     */
    fun createForegroundNotification(context: Context, text: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(BotService.ACTION_NOTIFICATION_DISMISSED).apply {
            setPackage(context.packageName)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_bot)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deletePendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
