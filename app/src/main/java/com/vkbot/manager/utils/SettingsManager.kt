package com.vkbot.manager.utils

import android.content.Context
import android.content.SharedPreferences
import com.vkbot.manager.ai.AiAssistant
import com.vkbot.manager.ai.AiConfig
import com.vkbot.manager.ai.AiProvider

/**
 * Менеджер для сохранения и загрузки глобальных настроек бота.
 */
object SettingsManager {

    private const val PREFS_NAME = "vkbot_global_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isAntiSpamEnabled: Boolean
        get() = prefs.getBoolean("pref_antispam_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_antispam_enabled", value).apply()

    var isAutoBanEnabled: Boolean
        get() = prefs.getBoolean("pref_autoban_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_autoban_enabled", value).apply()

    var isRandomFallbackEnabled: Boolean
        get() = prefs.getBoolean("pref_random_fallback_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_random_fallback_enabled", value).apply()

    var isFallbackSilenceEnabled: Boolean
        get() = prefs.getBoolean("pref_fallback_silence_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_fallback_silence_enabled", value).apply()

    var isMarkAsReadEnabled: Boolean
        get() = prefs.getBoolean("pref_mark_as_read_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_mark_as_read_enabled", value).apply()

    var isChatsEnabled: Boolean
        get() = prefs.getBoolean("pref_chats_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_chats_enabled", value).apply()

    var chatPrefix: String
        get() = prefs.getString("pref_chat_prefix", "Бот,") ?: "Бот,"
        set(value) = prefs.edit().putString("pref_chat_prefix", value).apply()
        
    /** Записывать ли вопросы без ответа в журнал «Не знаю ответа». */
    var isUnansweredLogEnabled: Boolean
        get() = prefs.getBoolean("pref_unanswered_log_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_unanswered_log_enabled", value).apply()

    /** ID владельцев (VK или Telegram) через запятую — только им доступны команды «!запомни», «!забудь». */
    var ownerIds: String
        get() = prefs.getString("pref_owner_ids", "") ?: ""
        set(value) = prefs.edit().putString("pref_owner_ids", value).apply()

    // --- ИИ-помощник ---

    /** Настройки ИИ. Ключ и модель хранятся отдельно для каждого поставщика, ключ — зашифрованным. */
    var aiConfig: AiConfig
        get() {
            val provider = AiProvider.fromId(prefs.getString("pref_ai_provider", null))
            return AiConfig(
                enabled = prefs.getBoolean("pref_ai_enabled", false),
                provider = provider,
                model = prefs.getString("pref_ai_model_${provider.id}", null) ?: provider.defaultModel,
                apiKey = decryptKey(prefs.getString("pref_ai_key_${provider.id}", "").orEmpty()),
                baseUrl = prefs.getString("pref_ai_base_url", "").orEmpty(),
                folderId = prefs.getString("pref_ai_folder_id", "").orEmpty(),
                persona = prefs.getString("pref_ai_persona", null) ?: AiConfig.DEFAULT_PERSONA,
                maxChars = prefs.getInt("pref_ai_max_chars", 300),
                dailyLimit = prefs.getInt("pref_ai_daily_limit", 200),
                inChats = prefs.getBoolean("pref_ai_in_chats", false)
            )
        }
        set(c) = prefs.edit()
            .putBoolean("pref_ai_enabled", c.enabled)
            .putString("pref_ai_provider", c.provider.id)
            .putString("pref_ai_model_${c.provider.id}", c.model.trim())
            .putString("pref_ai_key_${c.provider.id}", storedKeyFor(c.provider, c.apiKey.trim()))
            .putString("pref_ai_base_url", c.baseUrl.trim())
            .putString("pref_ai_folder_id", c.folderId.trim())
            .putString("pref_ai_persona", c.persona)
            .putInt("pref_ai_max_chars", c.maxChars.coerceIn(50, 2000))
            .putInt("pref_ai_daily_limit", c.dailyLimit.coerceAtLeast(0))
            .putBoolean("pref_ai_in_chats", c.inChats)
            .apply()

    /** Ключ и модель, сохранённые для другого поставщика (при переключении в настройках). */
    fun aiConfigFor(provider: AiProvider): AiConfig = aiConfig.copy(
        provider = provider,
        model = prefs.getString("pref_ai_model_${provider.id}", null) ?: provider.defaultModel,
        apiKey = decryptKey(prefs.getString("pref_ai_key_${provider.id}", "").orEmpty())
    )

    /** Сколько запросов к ИИ сделано сегодня (сбрасывается в полночь). */
    val aiUsage = object : AiAssistant.DailyUsage {
        private fun todayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        override fun today(): Int =
            if (prefs.getString("pref_ai_usage_date", "") == todayKey()) prefs.getInt("pref_ai_usage_count", 0) else 0
        override fun increment() {
            prefs.edit().putString("pref_ai_usage_date", todayKey()).putInt("pref_ai_usage_count", today() + 1).apply()
        }
    }

    /** Ключ не менялся — оставляем прежнюю зашифрованную строку (не шифруем заново при каждом сохранении). */
    private fun storedKeyFor(provider: AiProvider, key: String): String {
        val stored = prefs.getString("pref_ai_key_${provider.id}", "").orEmpty()
        return if (decryptKey(stored) == key) stored else encryptKey(key)
    }

    private fun encryptKey(key: String): String =
        if (key.isEmpty()) "" else runCatching { TokenCrypto.encrypt(key) }.getOrDefault(key)

    private fun decryptKey(stored: String): String = if (stored.isEmpty()) "" else TokenCrypto.decrypt(stored)

    /** Сколько сообщений за 4 секунды разрешено до срабатывания антиспама. */
    const val SPAM_LIMIT_NORMAL = 5
    const val SPAM_LIMIT_STRICT = 3

    var spamLimit: Int
        // Старые версии сохраняли 1 для «строгого» режима — это банило за 2 быстрых сообщения
        get() = prefs.getInt("pref_spam_limit", 10).coerceAtLeast(SPAM_LIMIT_STRICT)
        set(value) = prefs.edit().putInt("pref_spam_limit", value).apply()
}
