package com.vkbot.manager.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Бот Telegram (токен от @BotFather).
 */
data class Bot(
    val id: Int,
    var name: String,
    var token: String,
    var processedMessages: Long = 0,
    var answeredMessages: Long = 0,
    var startTime: Long = 0
)

/**
 * Единый менеджер управления данными ботов.
 * Решает проблему разрозненного хранения и ошибок доступа к файлам.
 */
object BotDataManager {
    private const val TAG = "BotDataManager"
    private const val BOTS_FILE_NAME = "bots.json"
    private const val PREFS_NAME = "vk_bot_settings"

    /** Приложение работает только с одним ботом. */
    const val MAX_BOTS = 1
    /** Метка платформы в bots.json: боты без неё — из VK-версий. */
    private const val PLATFORM_TELEGRAM = "telegram"

    private val TOKEN_PREF_KEY = Regex("bot_\\d+_token")

    /**
     * Загружает бота из bots.json. Боты VK и лишние боты из старых версий (до 5 штук) отбрасываются.
     */
    fun loadBots(context: Context): List<Bot> {
        val list = mutableListOf<Bot>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            val file = File(context.filesDir, BOTS_FILE_NAME)
            if (file.exists()) {
                val jsonString = file.readText(Charsets.UTF_8)
                if (jsonString.isNotEmpty()) {
                    val arr = JSONArray(jsonString)
                    var hasPlainToken = false
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optInt("id", i + 1)
                        val name = obj.optString("name", "")
                        val storedToken = obj.optString("token", "")
                        if (storedToken.isNotEmpty() && !TokenCrypto.isEncrypted(storedToken)) hasPlainToken = true
                        // Пустой токен (не расшифровался на другом устройстве) — бот остаётся, токен вводится заново
                        val token = TokenCrypto.decrypt(storedToken)

                        // Боты VK из прошлых версий больше не поддерживаются
                        if (obj.optString("platform") != PLATFORM_TELEGRAM) {
                            Log.i(TAG, "Бот «$name» (VK) пропущен: приложение работает только с Telegram")
                            continue
                        }
                        if (name.isNotEmpty()) {
                            list.add(Bot(
                                id = id,
                                name = name,
                                token = token,
                                processedMessages = prefs.getLong("bot_${id}_processed", 0),
                                answeredMessages = prefs.getLong("bot_${id}_answered", 0),
                                startTime = prefs.getLong("bot_${id}_start_time", 0)
                            ))
                        }
                    }
                    // Файл от старой версии: шифруем токены сразу
                    if (hasPlainToken && !keystoreBroken) saveBots(context, list)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading bots", e)
        }

        return list.take(MAX_BOTS)
    }

    /**
     * Сохраняет список ботов во внутреннюю память (токен — зашифрованным) и SharedPreferences (без токена).
     */
    fun saveBots(context: Context, bots: List<Bot>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Сохраняем в SharedPreferences для быстрого доступа сервиса
        prefs.edit {
            val activeIds = bots.map { it.id }.joinToString(",")
            putString("active_bot_ids", activeIds)

            // Старые версии хранили токены открытым текстом — стираем
            prefs.all.keys.filter { TOKEN_PREF_KEY.matches(it) }.forEach { remove(it) }

            bots.forEach { bot ->
                putString("bot_${bot.id}_name", bot.name)
                // Флаг «включён» больше не нужен: бот с токеном запускается кнопкой на главной
                remove("bot_${bot.id}_running")
            }
        }

        // 2. Сохраняем в JSON файл (внутренняя память)
        try {
            val file = File(context.filesDir, BOTS_FILE_NAME)
            val jsonArray = JSONArray()
            bots.forEach { bot ->
                jsonArray.put(JSONObject().apply {
                    put("id", bot.id)
                    put("name", bot.name)
                    put("token", encryptOrPlain(bot.token))
                    put("platform", PLATFORM_TELEGRAM)
                })
            }
            file.writeText(jsonArray.toString(4), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bots to file", e)
        }
    }

    /** Если Keystore недоступен, лучше сохранить токен как есть, чем потерять его. */
    private fun encryptOrPlain(token: String): String = try {
        TokenCrypto.encrypt(token)
    } catch (e: Exception) {
        Log.e(TAG, "Keystore недоступен, токен сохранён без шифрования", e)
        keystoreBroken = true
        token
    }

    /** Не пытаться пересохранять токены при каждой загрузке, если Keystore не работает. */
    @Volatile private var keystoreBroken = false
}
