package com.vkbot.manager.utils

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Последние сообщения и ответы бота для главного экрана. Хранятся только на телефоне,
 * не больше [MAX] штук, тексты обрезаются до [MAX_TEXT] символов.
 */
object RecentDialog {

    data class Item(val time: Long, val name: String, val message: String, val reply: String)

    const val MAX = 5
    private const val MAX_TEXT = 200
    private const val PREFS = "vk_bot_settings"
    private const val KEY = "recent_dialog"

    @Synchronized
    fun add(context: Context, name: String, message: String, reply: String, now: Long = System.currentTimeMillis()) {
        val items = (listOf(Item(now, name, message.take(MAX_TEXT), reply.take(MAX_TEXT))) + load(context)).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, toJson(items)) }
    }

    /** Новые — первыми. */
    fun load(context: Context): List<Item> =
        fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty())

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY) }

    internal fun toJson(items: List<Item>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("t", it.time).put("n", it.name).put("m", it.message).put("r", it.reply)) }
    }.toString()

    internal fun fromJson(json: String): List<Item> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { Item(it.optLong("t"), it.optString("n"), it.optString("m"), it.optString("r")) }
        }
    }
}
