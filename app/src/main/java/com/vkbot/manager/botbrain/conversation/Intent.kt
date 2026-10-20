package com.vkbot.manager.botbrain.conversation

/** Простые признаки фразы: обращение к боту, приветствие, вопрос. */
object Intent {

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    /** Обращения к боту в начале фразы («Бот, …», «Киро, …»). */
    private val APPEALS = setOf("бот", "ботик", "киро", "kiro", "слушай", "эй")

    private val QUESTION_WORDS = setOf(
        "как", "какой", "какая", "какое", "какие", "что", "че", "чо", "где", "когда", "куда", "откуда",
        "почему", "зачем", "сколько", "кто", "чей", "ли", "можно", "умеешь", "знаешь"
    )

    /**
     * Убирает обращение и приветствие в начале, если после них есть что-то ещё:
     * «Бот, привет, сколько будет 2+2» → «сколько будет 2+2». Исходный регистр и знаки сохраняются.
     */
    fun stripAppeal(text: String, rules: ConversationRules): String {
        val spans = WORD.findAll(text).toList()
        var i = 0
        while (i < spans.size - 1) {
            val w = ConversationRules.words(spans[i].value).firstOrNull() ?: break
            if (w in APPEALS || isGreetingWord(w, rules)) i++ else break
        }
        return if (i == 0) text.trim() else text.substring(spans[i].range.first).trim()
    }

    /** Фраза — только приветствие (можно с обращением): «привет», «привет бот», «добрый вечер». */
    fun isGreeting(words: List<String>, rules: ConversationRules): Boolean {
        val meaningful = words.filter { it !in APPEALS }
        if (meaningful.isEmpty()) return false
        return rules.matchesWhole(GREETING, meaningful) || meaningful.all { isGreetingWord(it, rules) }
    }

    private fun isGreetingWord(word: String, rules: ConversationRules): Boolean =
        rules.triggers(GREETING).any { it.size == 1 && it[0] == word }

    /** Похоже на вопрос: знак «?» или вопросительное слово в начале. */
    fun isQuestion(text: String): Boolean {
        if (text.trimEnd().endsWith("?")) return true
        val first = ConversationRules.words(text).firstOrNull() ?: return false
        return first in QUESTION_WORDS
    }

    const val GREETING = "greeting"
}
