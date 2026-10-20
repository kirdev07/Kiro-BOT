package com.vkbot.manager.botbrain.conversation.functions

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Безопасный калькулятор: + − × ÷ %, степень ^, скобки, дробные числа.
 * Только разбор арифметики (без eval и скриптов), с ограничениями длины, вложенности и размера чисел.
 */
object SafeCalculator {

    class DivisionByZero : Exception()

    private const val MAX_LENGTH = 80
    private const val MAX_DEPTH = 20
    private const val MAX_ABS = 1e15
    private const val MAX_POWER = 100.0
    private val DIGITS_APART = Regex("[\\d.]\\s+[\\d.]")

    /** @return значение или null, если это не корректное выражение. @throws DivisionByZero */
    fun evaluate(expression: String): Double? {
        // «+7 999 123 45 67» — номер телефона, а не выражение: числа через пробел без знака
        if (DIGITS_APART.containsMatchIn(expression)) return null
        val src = expression.replace(" ", "")
        if (src.isEmpty() || src.length > MAX_LENGTH) return null
        return try {
            val parser = Parser(src)
            val value = parser.expression(0)
            if (!parser.atEnd() || value.isNaN() || value.isInfinite() || abs(value) > MAX_ABS) null else value
        } catch (e: DivisionByZero) {
            throw e
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** 4.0 → «4», 0.3333333 → «0.333333», 1e15 → «1000000000000000». */
    fun format(value: Double): String {
        if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
        return "%.6f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        private fun peek(): Char? = s.getOrNull(pos)

        fun expression(depth: Int): Double {
            require(depth <= MAX_DEPTH)
            var value = term(depth)
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + term(depth) }
                    '-' -> { pos++; value - term(depth) }
                    else -> return value
                }
            }
        }

        private fun term(depth: Int): Double {
            var value = power(depth)
            while (true) {
                value = when (peek()) {
                    '*' -> { pos++; value * power(depth) }
                    '/' -> { pos++; val d = power(depth); if (d == 0.0) throw DivisionByZero(); value / d }
                    '%' -> { pos++; val d = power(depth); if (d == 0.0) throw DivisionByZero(); value % d }
                    else -> return value
                }
            }
        }

        private fun power(depth: Int): Double {
            val base = unary(depth)
            if (peek() != '^') return base
            pos++
            val exponent = power(depth + 1)
            require(abs(exponent) <= MAX_POWER)
            return base.pow(exponent)
        }

        private fun unary(depth: Int): Double = when (peek()) {
            '-' -> { pos++; -unary(depth + 1) }
            '+' -> { pos++; unary(depth + 1) }
            else -> primary(depth)
        }

        private fun primary(depth: Int): Double {
            if (peek() == '(') {
                pos++
                val value = expression(depth + 1)
                require(peek() == ')')
                pos++
                return value
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            require(pos > start)
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException()
        }
    }
}
