package com.vkbot.manager.botbrain

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Журнал «Не знаю ответа»: сообщения, на которые в базе не нашлось ответа, со счётчиком.
 * Одинаковые по смыслу («Как дела?» и «как дела») считаются одной записью.
 * Хранятся только тексты — без имён и ID собеседников.
 */
class UnansweredLog(private val maxEntries: Int = MAX_ENTRIES) {

    /** [suggestion] — последний ответ ИИ на этот вопрос (подставляется в редакторе). */
    data class Entry(val text: String, val count: Int, val lastSeen: Long, val suggestion: String = "")

    private val entries = LinkedHashMap<String, Entry>()
    private val dirty = AtomicBoolean(false)

    /** Есть несохранённые изменения. */
    val isDirty: Boolean get() = dirty.get()

    fun markSaved() = dirty.set(false)

    @Synchronized
    fun record(text: String, now: Long = System.currentTimeMillis(), suggestion: String = "") {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_TEXT_LENGTH || clean.startsWith("!") || clean.startsWith("/")) return
        val key = keyOf(clean)
        if (key.isEmpty()) return
        val old = entries[key]
        entries[key] = Entry(clean, (old?.count ?: 0) + 1, now, suggestion.trim().ifEmpty { old?.suggestion.orEmpty() })
        if (entries.size > maxEntries) {
            // Вытесняем самую редкую и давнюю запись
            entries.minWithOrNull(compareBy<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.value.lastSeen })
                ?.let { entries.remove(it.key) }
        }
        dirty.set(true)
    }

    /** Чаще всего задаваемые — первыми. */
    @Synchronized
    fun top(limit: Int = maxEntries): List<Entry> =
        entries.values.sortedWith(compareByDescending<Entry> { it.count }.thenByDescending { it.lastSeen }).take(limit)

    @Synchronized
    fun remove(text: String): Boolean {
        val removed = entries.remove(keyOf(text)) != null
        if (removed) dirty.set(true)
        return removed
    }

    /** Убирает записи, на которые в базе теперь есть ответ. @return сколько убрано */
    @Synchronized
    fun prune(hasAnswer: (String) -> Boolean): Int {
        val answered = entries.filterValues { hasAnswer(it.text) }.keys
        answered.forEach { entries.remove(it) }
        if (answered.isNotEmpty()) dirty.set(true)
        return answered.size
    }

    @Synchronized
    fun clear() {
        if (entries.isNotEmpty()) dirty.set(true)
        entries.clear()
    }

    val size: Int @Synchronized get() = entries.size

    @Synchronized
    fun toJson(): String = JSONArray().apply {
        for (e in entries.values) put(JSONObject().put("text", e.text).put("count", e.count).put("last", e.lastSeen)
            .apply { if (e.suggestion.isNotEmpty()) put("ai", e.suggestion) })
    }.toString()

    companion object {
        const val MAX_ENTRIES = 300
        private const val MAX_TEXT_LENGTH = 300

        /** Нормализованный ключ: без регистра, знаков препинания, ё = е. */
        fun keyOf(text: String): String = TextAnalyzer.tokens(text, emptyMap()).joinToString(" ")

        fun fromJson(json: String, maxEntries: Int = MAX_ENTRIES): UnansweredLog {
            val log = UnansweredLog(maxEntries)
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return log
            synchronized(log) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val text = o.optString("text").trim()
                    val key = keyOf(text)
                    if (key.isNotEmpty()) log.entries[key] = Entry(text, o.optInt("count", 1), o.optLong("last"), o.optString("ai"))
                }
            }
            return log
        }
    }
}
