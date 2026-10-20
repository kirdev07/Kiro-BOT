package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.TextAnalyzer
import kotlin.random.Random

/**
 * Разговорные фразы из редактируемого файла conversation.txt (в папке kirdev_base).
 *
 * Формат:
 * ```
 * [раздел]
 * > фраза-триггер, ещё фраза, …
 * Вариант ответа
 * Ещё вариант ответа
 * ```
 * Строки «>» — на что реагировать, остальные — варианты ответа. Комментарии начинаются с «#».
 */
class ConversationRules private constructor(private val sections: Map<String, Section>) {

    class Section(val triggers: List<List<String>>, val responses: List<String>)

    fun section(name: String): Section? = sections[name]

    fun responses(name: String): List<String> = sections[name]?.responses.orEmpty()

    /** Триггеры раздела — списки слов (нижний регистр, ё = е). */
    fun triggers(name: String): List<List<String>> = sections[name]?.triggers.orEmpty()

    /** Сообщение целиком совпадает с одним из триггеров раздела. */
    fun matchesWhole(name: String, words: List<String>): Boolean = triggers(name).any { it == words }

    /**
     * Случайный вариант ответа, которого нет в истории диалога ([turnsSince] вернул null).
     * Если все варианты недавно звучали — тот, что звучал давнее всего.
     */
    fun pick(name: String, random: Random, turnsSince: (String) -> Int?): String? {
        val all = responses(name)
        if (all.isEmpty()) return null
        val fresh = all.filter { turnsSince(it) == null }
        if (fresh.isNotEmpty()) return fresh.random(random)
        return all.maxByOrNull { turnsSince(it) ?: Int.MAX_VALUE }
    }

    /**
     * Набор характера поверх основных фраз: его разделы заменяют одноимённые (пустой раздел — «без фраз»).
     * Триггеры (на что реагировать) остаются из основного файла, если в наборе их нет.
     */
    fun overriddenBy(pack: ConversationRules): ConversationRules {
        val merged = LinkedHashMap(sections)
        for ((name, packSection) in pack.sections) {
            val base = sections[name]
            merged[name] = Section(
                packSection.triggers.ifEmpty { base?.triggers.orEmpty() },
                packSection.responses
            )
        }
        return ConversationRules(merged)
    }

    companion object {
        const val FILE_NAME = "conversation.txt"

        /** Наборы характера: id → файл conversation_<id>.txt с заменой фраз. Пустой id — обычный. */
        val PERSONALITIES = listOf("", "warm", "cheeky", "polite")

        fun personalityFile(id: String): String = "conversation_$id.txt"

        val EMPTY = ConversationRules(emptyMap())

        fun parse(lines: List<String>): ConversationRules {
            val names = LinkedHashSet<String>()
            val triggers = LinkedHashMap<String, MutableList<List<String>>>()
            val responses = LinkedHashMap<String, MutableList<String>>()
            var current: String? = null
            for (raw in lines) {
                val line = raw.removePrefix(Char(0xFEFF).toString()).trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = line.substring(1, line.length - 1).trim().lowercase()
                    names += current
                    continue
                }
                val name = current ?: continue
                if (line.startsWith(">")) {
                    line.drop(1).split(",").map { words(it) }.filter { it.isNotEmpty() }
                        .let { triggers.getOrPut(name) { mutableListOf() }.addAll(it) }
                } else {
                    responses.getOrPut(name) { mutableListOf() }.add(line)
                }
            }
            return ConversationRules(names.associateWith { Section(triggers[it].orEmpty(), responses[it].orEmpty()) })
        }

        /** Слова фразы: нижний регистр, ё = е, без знаков препинания. */
        fun words(text: String): List<String> = TextAnalyzer.tokens(text, emptyMap())
    }
}
