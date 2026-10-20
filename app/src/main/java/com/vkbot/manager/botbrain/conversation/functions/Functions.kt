package com.vkbot.manager.botbrain.conversation.functions

import com.vkbot.manager.botbrain.conversation.ConversationContext
import com.vkbot.manager.botbrain.conversation.ConversationRules
import java.util.Calendar
import java.util.TimeZone

private fun phrases(vararg p: String) = p.map { ConversationRules.words(it) }

/** «Чай или кофе?», «выбери: пицца, суши или бургер» — выбор одного варианта. */
class ChoiceFunction : FunctionProcessor() {
    override val name = "или … или"

    private val prefixes = phrases("что лучше", "что круче", "что выбрать", "выбери", "как думаешь", "скажи", "что больше нравится")
    // \b в Java не видит границ кириллических слов — границы задаются явно
    private val separator = Regex("\\s*(?:,\\s*)?(?<!\\p{L})или(?!\\p{L})\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE)

    override fun evaluate(ctx: ConversationContext): Result? {
        if ("или" !in ctx.coreWords || ctx.coreWords.size > MAX_WORDS) return null
        val body = (afterPrefix(ctx.core, ctx.coreWords, prefixes) ?: ctx.core).trim(' ', '?', '!', '.', ':', '-')
        val options = body.split(separator).map { it.trim(' ', '?', '!', '.', ':') }.filter { it.isNotEmpty() }
        if (options.size !in 2..MAX_OPTIONS || options.any { ConversationRules.words(it).let { w -> w.isEmpty() || w.size > MAX_OPTION_WORDS } }) return null
        if (options.map { ConversationRules.words(it) }.distinct().size == 1) return Result("choice_both", "")
        return Result("choice", options.random(ctx.random))
    }

    companion object {
        const val MAX_WORDS = 12
        const val MAX_OPTIONS = 4
        const val MAX_OPTION_WORDS = 4
    }
}

/** «Инфа, что завтра повезёт» — шуточный процент. Один и тот же вопрос в один день — тот же процент. */
class ChanceFunction : FunctionProcessor() {
    override val name = "инфа"

    private val prefixes = phrases("инфа", "какова вероятность", "какая вероятность", "вероятность", "какой шанс", "каковы шансы", "какие шансы")

    override fun evaluate(ctx: ConversationContext): Result? {
        val rest = afterPrefix(ctx.core, ctx.coreWords, prefixes) ?: return null
        val subject = ConversationRules.words(rest)
        if (subject.isEmpty()) return null
        val day = ctx.now / (24 * 60 * 60 * 1000L)
        val percent = Math.floorMod((subject.joinToString(" ") + day).hashCode(), 101)
        return Result("chance", percent.toString())
    }
}

/**
 * «Когда я стану богатым?» — шуточный срок. Только личные вопросы (про меня / тебя / нас):
 * «когда началась вторая мировая» — настоящий вопрос, он идёт в базу и ИИ.
 */
class WhenFunction : FunctionProcessor() {
    override val name = "когда"

    private val personal = setOf("я", "мне", "меня", "мной", "мы", "нам", "нас", "ты", "тебе", "тебя", "мой", "моя", "мое", "мои", "наш", "наша", "наши")

    override fun evaluate(ctx: ConversationContext): Result? {
        val w = ctx.coreWords
        if (w.firstOrNull() != "когда" || w.size < 3 || w.size > 10 || w.none { it in personal }) return null
        val (n, unit) = when (ctx.random.nextInt(4)) {
            0 -> ctx.random.nextInt(2, 30) to UNITS[0]
            1 -> ctx.random.nextInt(2, 10) to UNITS[1]
            2 -> ctx.random.nextInt(2, 12) to UNITS[2]
            else -> ctx.random.nextInt(2, 15) to UNITS[3]
        }
        return Result("when", "$n ${plural(n, unit)}")
    }

    companion object {
        private val UNITS = listOf(listOf("день", "дня", "дней"), listOf("неделю", "недели", "недель"),
            listOf("месяц", "месяца", "месяцев"), listOf("год", "года", "лет"))

        /** 1 день, 2 дня, 5 дней, 21 день, 11 дней. */
        fun plural(n: Int, forms: List<String>): String {
            val mod100 = n % 100
            val mod10 = n % 10
            return when {
                mod100 in 11..14 -> forms[2]
                mod10 == 1 -> forms[0]
                mod10 in 2..4 -> forms[1]
                else -> forms[2]
            }
        }
    }
}

/** «Который час?», «сколько времени?» — локальное время телефона. */
class TimeFunction(private val timeZone: () -> TimeZone = { TimeZone.getDefault() }) : FunctionProcessor() {
    override val name = "время"

    private val questions = phrases("который час", "сколько времени", "сколько время", "сколько сейчас времени", "который сейчас час",
        "скажи время", "подскажи время", "подскажи который час", "скажи который час", "время сколько")

    override fun evaluate(ctx: ConversationContext): Result? {
        if (ctx.coreWords !in questions) return null
        val c = Calendar.getInstance(timeZone()).apply { timeInMillis = ctx.now }
        return Result("time", "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
    }
}

/** «Какое сегодня число?», «какой день недели?» — дата и день недели. */
class DateFunction(private val timeZone: () -> TimeZone = { TimeZone.getDefault() }) : FunctionProcessor() {
    override val name = "дата"

    private val questions = phrases("какое сегодня число", "какое число", "какое число сегодня", "сегодня какое число",
        "какой сегодня день", "какой день", "какой сегодня день недели", "какой день недели", "какая сегодня дата", "какая дата",
        "какое сегодня число и день недели", "что сегодня за день")

    override fun evaluate(ctx: ConversationContext): Result? {
        if (ctx.coreWords !in questions) return null
        val c = Calendar.getInstance(timeZone()).apply { timeInMillis = ctx.now }
        val weekday = WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1]
        return Result("date", "$weekday, ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}")
    }

    companion object {
        private val WEEKDAYS = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
        private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    }
}

/** «Сколько будет 2+2*2», «посчитай (5+3)/2», «15 * 4 =» — безопасный калькулятор. */
class MathFunction : FunctionProcessor() {
    override val name = "калькулятор"

    private val prefixes = phrases("сколько будет", "посчитай", "вычисли", "реши", "подсчитай", "сколько")
    private val allowed = Regex("^[0-9\\s+\\-*/^%().,x×х÷:=?]+$", RegexOption.IGNORE_CASE)
    /** Даты и номера похожи на выражения — их не считаем. */
    private val dateLike = Regex("^\\s*\\d{1,2}\\s*[./]\\s*\\d{1,2}\\s*[./]\\s*\\d{2,4}\\s*$")
    private val strongOperator = Regex("[+*/^×х÷%]|\\d\\s*:\\s*\\d")

    override fun evaluate(ctx: ConversationContext): Result? {
        val withPrefix = afterPrefix(ctx.core, ctx.coreWords, prefixes)
        val expr = (withPrefix ?: ctx.core).trim().trimEnd('?', '=', ' ').trimEnd('?', '=', ' ')
        if (expr.isEmpty() || !allowed.matches(expr) || !expr.any { it.isDigit() } || dateLike.matches(expr)) return null
        // Без слова «посчитай» считаем только явные выражения: «10-15» может быть диапазоном
        if (withPrefix == null && !strongOperator.containsMatchIn(expr)) return null
        val normalized = expr.replace(Regex("[x×хX]", RegexOption.IGNORE_CASE), "*").replace('÷', '/').replace(':', '/').replace(',', '.')
        return try {
            SafeCalculator.evaluate(normalized)?.let { Result("math", "$expr = ${SafeCalculator.format(it)}") }
        } catch (e: SafeCalculator.DivisionByZero) {
            Result("math_error", "")
        }
    }
}

/** «Рандом 1 100», «случайное число от 5 до 10», «брось кубик» — случайное число. */
class RandomFunction : FunctionProcessor() {
    override val name = "рандом"

    private val prefixes = phrases("рандом", "random", "случайное число", "случайно", "брось кубик", "кинь кубик", "кубик")
    private val fillers = setOf("от", "до", "и", "из", "между", "число")

    override fun evaluate(ctx: ConversationContext): Result? {
        val rest = afterPrefix(ctx.core, ctx.coreWords, prefixes) ?: return null
        val dice = "кубик" in ctx.coreWords
        // «1-100» — диапазон, а не отрицательное число
        val tokens = Regex("-?\\d+|[\\p{L}]+").findAll(rest.replace(Regex("(\\d)\\s*[-–—]\\s*(\\d)"), "$1 $2")).map { it.value }.filter { it.lowercase() !in fillers }.toList()
        if (tokens.any { it.toLongOrNull() == null } || tokens.size > 2) return null
        val numbers = tokens.map { it.toLong() }
        var (from, to) = when (numbers.size) {
            0 -> 1L to if (dice) 6L else 100L
            1 -> 1L to numbers[0]
            else -> numbers[0] to numbers[1]
        }
        if (from > to) from = to.also { to = from }
        if (to - from > MAX_RANGE) return null
        return Result("random", (from + ctx.random.nextLong(to - from + 1)).toString())
    }

    companion object {
        const val MAX_RANGE = 1_000_000_000_000L
    }
}
