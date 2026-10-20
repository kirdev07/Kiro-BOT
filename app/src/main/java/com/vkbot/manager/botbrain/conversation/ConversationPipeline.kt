package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.Attachment
import com.vkbot.manager.botbrain.BotMessage
import com.vkbot.manager.botbrain.SearchResult
import kotlin.random.Random

/**
 * Разговорный конвейер: обработчики по убыванию приоритета, первый ответивший побеждает.
 * Обработчик возвращает [Reply] или null — тогда сообщение уходит следующему.
 * Здесь нет ничего про Telegram API: только текст сообщения и история разговора.
 */
class ConversationPipeline(processors: List<ConversationProcessor>) {

    val processors: List<ConversationProcessor> = processors.sortedByDescending { it.priority }

    fun run(ctx: ConversationContext): Reply? {
        processors.filterIsInstance<ConversationObserver>().forEach { it.observe(ctx) }
        for (p in processors) {
            ctx.tried += p.name
            p.process(ctx)?.let { return it }
        }
        return null
    }
}

interface ConversationProcessor {
    /** Имя для отладки («почему бот ответил так»). */
    val name: String
    /** Больше — раньше. См. [Priority]. */
    val priority: Int
    fun process(ctx: ConversationContext): Reply?
}

/** Обработчик, которому нужно видеть каждое сообщение, даже если ответит другой (запоминание имени). */
interface ConversationObserver {
    fun observe(ctx: ConversationContext)
}

/**
 * Приоритеты обработчиков. Правило: разговорная функция не перехватывает то,
 * что автор базы задал явно (команды, контекстные ответы, точные фразы).
 */
object Priority {
    /** Ответы базы, продолжающие контекст диалога (requiredContext совпал). */
    const val DATABASE_CONTEXT = 100
    /** «Как меня зовут?», когда собеседник называл имя, — память диалога точнее базы. */
    const val SESSION_FACTS = 98
    /** «А ты?» на свежий вопрос — ответ на тот же вопрос про бота. */
    const val MIRROR_FOLLOW_UP = 95
    /** Точное совпадение фразы с вопросом из базы и команды (regex / маски). */
    const val DATABASE_EXACT = 90
    /** «Или … или», «инфа», время, дата, калькулятор, рандом. */
    const val FUNCTIONS = 85
    /** Короткие продолжения («да», «почему?», «ясно»), если база на них не ответила. */
    const val FOLLOW_UP = 70
    /** Похвала / ругань в адрес бота. */
    const val SENTIMENT = 60
    /** Совпадение по смыслу слов, несколько вопросов в одном сообщении. */
    const val DATABASE_SMART = 50
}

enum class ReplyKind {
    /** Ответ из базы (в том числе команды с картинками). */
    DATABASE,
    FUNCTION,
    REACTION,
    FOLLOW_UP,
    FACT,
    AI,
    FALLBACK
}

data class Reply(
    val text: String,
    val kind: ReplyKind,
    val processor: String,
    val attachments: List<Attachment> = emptyList(),
    /** Выбранный ответ базы (для отладки и контекста). */
    val result: SearchResult? = null
)

/** Всё, что обработчику нужно знать о сообщении и разговоре. */
class ConversationContext(
    val message: BotMessage,
    val dialog: DialogHistory.Dialog,
    val rules: ConversationRules,
    val random: Random,
    val now: Long,
    /** ИИ включён: неуверенные совпадения базы уступают ему. */
    val aiAvailable: Boolean = false,
    private val searcher: (text: String, context: String) -> List<SearchResult>
) {
    /** Неуверенный ответ базы, отложенный ради ИИ: используется, если ИИ не ответил. */
    var deferred: List<SearchResult> = emptyList()

    val text: String = message.text.trim()
    val words: List<String> by lazy { ConversationRules.words(text) }

    /** Текст без обращения и приветствия в начале: «Бот, привет, сколько будет 2+2» → «сколько будет 2+2». */
    val core: String by lazy { Intent.stripAppeal(text, rules) }
    val coreWords: List<String> by lazy { ConversationRules.words(core) }

    /** Контекст из предыдущего ответа базы (снимок на момент прихода сообщения). */
    val context: String = dialog.context()

    /** Результаты поиска всего сообщения в базе (считаются один раз). */
    val search: List<SearchResult> by lazy { searcher(text, context) }

    fun searchFor(query: String): List<SearchResult> = searcher(query, context)

    /** Имя собеседника: названное им самим в разговоре, иначе из профиля. */
    val userName: String get() = dialog.fact(SessionFactsProcessor.NAME) ?: message.authorName.ifEmpty { "Пользователь" }

    /** Какие обработчики успели посмотреть сообщение (для отладки). */
    val tried = mutableListOf<String>()
}
