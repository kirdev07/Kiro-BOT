package com.vkbot.manager.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class BlockedUser(val id: Long, val name: String)

/**
 * Менеджер для хранения и управления Черным Списком (Blacklist).
 * Сохраняет ID и имена пользователей в локальный JSON файл.
 */
object BlacklistManager {
    private const val TAG = "BlacklistManager"
    private const val FILE_NAME = "blacklist.json"
    
    // Используем потокобезопасную коллекцию Key -> Object
    private val blacklistedUsers = ConcurrentHashMap<Long, BlockedUser>()
    private var file: File? = null

    /**
     * Инициализация менеджера (загрузка из файла)
     */
    fun init(context: Context) {
        if (file == null) {
            file = File(context.filesDir, FILE_NAME)
            load()
            Log.i(TAG, "Инициализирован. В ЧС: ${blacklistedUsers.size} пользователей.")
        }
    }

    /**
     * Проверка: находится ли пользователь в ЧС
     */
    fun isBlacklisted(userId: Long): Boolean {
        return blacklistedUsers.containsKey(userId)
    }

    /**
     * Добавление пользователя в ЧС
     */
    fun add(userId: Long, name: String = "Неизвестный ID") {
        if (!blacklistedUsers.containsKey(userId)) {
            blacklistedUsers[userId] = BlockedUser(userId, name)
            Log.i(TAG, "Пользователь $userId ($name) добавлен в ЧС")
            save()
        }
    }

    /**
     * Удаление пользователя из ЧС
     */
    fun remove(userId: Long) {
        if (blacklistedUsers.remove(userId) != null) {
            Log.i(TAG, "Пользователь $userId удален из ЧС")
            save()
        }
    }

    /**
     * Получение всего списка заблокированных
     */
    fun getAll(): List<BlockedUser> {
        return blacklistedUsers.values.toList()
    }

    private fun load() {
        val safeFile = file ?: return
        if (safeFile.exists()) {
            try {
                val jsonStr = safeFile.readText()
                if (jsonStr.isNotBlank()) {
                    val array = JSONArray(jsonStr)
                    blacklistedUsers.clear()
                    
                    for (i in 0 until array.length()) {
                        val element = array.get(i)
                        // Поддержка старого формата (массив чисел) и нового (массив JSONObject)
                        if (element is Number) {
                            val id = element.toLong()
                            blacklistedUsers[id] = BlockedUser(id, "ID: $id")
                        } else if (element is JSONObject) {
                            val id = element.getLong("id")
                            val name = element.optString("name", "ID: $id")
                            blacklistedUsers[id] = BlockedUser(id, name)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки списка: ${e.message}")
            }
        }
    }

    private fun save() {
        val safeFile = file ?: return
        try {
            val array = JSONArray()
            blacklistedUsers.values.forEach { user ->
                val obj = JSONObject()
                obj.put("id", user.id)
                obj.put("name", user.name)
                array.put(obj)
            }
            safeFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения списка: ${e.message}")
        }
    }
}

