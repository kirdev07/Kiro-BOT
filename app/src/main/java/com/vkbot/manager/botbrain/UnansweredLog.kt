package com.vkbot.manager.botbrain

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Журнал «Не знаю ответа»: сообщения, на которые в базе не нашлось ответа, со счётчиком.
 * Похожие вопросы объединяются в группу: «Как дела?» = «как дела», «какая погода в москве» ≈ «какая погода в москве сегодня».
 * Мусор (1–2 символа, ссылки, команды, «аааааа») не записывается. Хранятся только тексты — без имён и ID.
 */
class UnansweredLog(private val maxEntries: Int = MAX_ENTRIES) {

    /** [text] — пример вопроса группы, [count] — сколько раз спрашивали, [suggestion] — последний ответ ИИ. */
    data class Entry(val text: String, val count: Int, val lastSeen: Long, val suggestion: String = "")

    private val entries = LinkedHashMap<String, Entry>()
    private val dirty = AtomicBoolean(false)

    /** Есть несохранённые изменения. */
    val isDirty: Boolean get() = dirty.get()

    fun markSaved() = dirty.set(false)

    @Synchronized
    fun record(text: String, now: Long = System.currentTimeMillis(), suggestion: String = "") {
        val clean = text.trim()
        if (isJunk(clean)) return
        val key = groupKeyOf(clean)
        if (key.isEmpty()) return
        val target = findGroup(key) ?: key
        val old = entries[target]
        // Пример группы обновляем только для той же формулировки — иначе по нему не найти группу
        val example = if (old == null || target == key) clean else old.text
        entries[target] = Entry(example, (old?.count ?: 0) + 1, now, suggestion.trim().ifEmpty { old?.suggestion.orEmpty() })
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
        val key = findGroup(groupKeyOf(text)) ?: return false
        entries.remove(key)
        dirty.set(true)
        return true
    }

    /** Убирает группы, на которые в базе теперь есть ответ. @return сколько убрано */
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

    /** Та же группа или очень похожая (≥ [SIMILARITY] слов совпадают, с учётом опечаток). */
    private fun findGroup(key: String): String? {
        if (key in entries) return key
        val stems = key.split(' ')
        if (stems.size < MIN_WORDS_FOR_SIMILARITY) return null
        return entries.keys.firstOrNull { other -> similarity(stems, other.split(' ')) >= SIMILARITY }
    }

    companion object {
        const val MAX_ENTRIES = 300
        private const val MAX_TEXT_LENGTH = 300
        private const val MIN_TEXT_LENGTH = 3
        /** Доля совпавших слов, чтобы вопросы считались одной группой. */
        const val SIMILARITY = 0.75
        private const val MIN_WORDS_FOR_SIMILARITY = 2

        private val URL = Regex("(?i)(?:https?://|www\\.)\\S+|[\\p{L}\\p{N}-]+\\.(?:ru|com|net|org|io|me|su|рф)\\S*")
        private val REPEATED_CHAR = Regex("(.)\\1{5,}")
        private val LONG_WORD = Regex("[^\\s]{31,}")
        /** Слова, не влияющие на смысл вопроса в журнале. */
        private val FILLER = setOf("а", "и", "ну", "же", "ли", "вот", "в", "во", "на", "с", "у", "о", "к", "по", "за", "бот", "слушай",
            "скажи", "подскажи", "пожалуйста", "плиз", "пж", "мне", "ка", "то", "бы")

        /** Нормализованный ключ фразы (без регистра, знаков, ё = е) — для точного сравнения вопросов базы. */
        fun keyOf(text: String): String = TextAnalyzer.tokens(text, emptyMap()).joinToString(" ")

        /** Ключ группы: основы значимых слов по алфавиту — порядок слов и формы не важны. */
        fun groupKeyOf(text: String): String =
            TextAnalyzer.tokens(text, emptyMap()).filter { it !in FILLER }.map { TextAnalyzer.stem(it) }.distinct().sorted().joinToString(" ")

        /** Не вопрос, а мусор: слишком коротко, только ссылка, команда, «аааааа», бессмысленно длинное «слово». */
        fun isJunk(text: String): Boolean {
            if (text.length < MIN_TEXT_LENGTH || text.length > MAX_TEXT_LENGTH) return true
            if (text.startsWith("!") || text.startsWith("/")) return true
            if (text.replace(URL, "").count { it.isLetter() } < 2) return true
            return REPEATED_CHAR.containsMatchIn(text) || LONG_WORD.containsMatchIn(text.replace(URL, ""))
        }

        private fun similarity(a: List<String>, b: List<String>): Double {
            val matched = a.count { x -> b.any { y -> x == y || isTypo(x, y) } }
            return matched.toDouble() / max(a.size, b.size)
        }

        private fun isTypo(a: String, b: String): Boolean {
            if (min(a.length, b.length) < 4 || a[0] != b[0] || kotlin.math.abs(a.length - b.length) > 1) return false
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1).also { it[0] = i }
                for (j in 1..b.length) cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
            return prev[b.length] <= 1
        }

        fun fromJson(json: String, maxEntries: Int = MAX_ENTRIES): UnansweredLog {
            val log = UnansweredLog(maxEntries)
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return log
            synchronized(log) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val text = o.optString("text").trim()
                    val key = groupKeyOf(text)
                    if (key.isEmpty()) continue
                    // Старые записи с разными ключами могут попасть в одну группу — счётчики складываем
                    val old = log.entries[key]
                    val entry = Entry(text, o.optInt("count", 1), o.optLong("last"), o.optString("ai"))
                    log.entries[key] = if (old == null) entry else Entry(
                        if (entry.lastSeen >= old.lastSeen) entry.text else old.text, old.count + entry.count,
                        max(old.lastSeen, entry.lastSeen), entry.suggestion.ifEmpty { old.suggestion }
                    )
                }
            }
            return log
        }
    }
}
