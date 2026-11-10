package com.sanjay.pocketmind.core.tools

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exact arithmetic for the model. Language models predict digits instead of calculating, so even
 * "25 + 26" can come out wrong; with this tool the model writes the expression and reads the result.
 */
class CalculatorTool : LocalTool {
    override val name = NAME
    override val description =
        "Calculates an arithmetic expression exactly. Use it whenever the answer needs a calculation with " +
            "numbers, instead of doing maths in your head; not for questions without arithmetic. Supports " +
            "+ - * / ^, parentheses, percentages like 15%, sqrt(), abs(), round(), ln(), log() and the constants " +
            "pi and e."
    override val parameters = listOf(
        ToolParameter("expression", ParameterType.STRING, "The expression, for example (123.4 * 56) / 3"),
    )

    override fun run(arguments: JsonObject): JsonObject {
        val expression = arguments.requireText("expression")
        val result = Expression.evaluate(expression)
        return buildJsonObject {
            put("expression", expression)
            put("result", Expression.format(result))
        }
    }

    companion object {
        const val NAME = "calculator"
    }
}

/**
 * A small recursive-descent parser over [BigDecimal], so decimal sums are exact (0.1 + 0.2 = 0.3,
 * not 0.30000000000000004). Grammar, lowest precedence first:
 * ```
 * sum     = product (("+" | "-") product)*
 * product = unary (("*" | "/") unary)*
 * unary   = ("-" | "+") unary | power      (-3^2 = -(3^2) = -9, as in maths)
 * power   = postfix ("^" unary)?           (right-associative: 2^3^2 = 2^9; 2^-3 works)
 * postfix = primary "%"?                   (15% = 0.15)
 * primary = number | "(" sum ")" | function "(" sum ")" | constant
 * ```
 */
object Expression {
    /** 34 significant digits for division and roots: far beyond what an answer shows. */
    private val CONTEXT = DECIMAL128

    /** Digits shown in a result. */
    private const val SHOWN_DIGITS = 15

    /** "15%" = 15 × 10^-2: the decimal point moves two places left. */
    private const val PERCENT_SHIFT = -2

    fun evaluate(expression: String): BigDecimal {
        val parser = Parser(normalize(expression))
        val value = parser.sum()
        parser.expectEnd()
        return value
    }

    /** Plain digits (no "1E+3"), at most [SHOWN_DIGITS] significant digits, no trailing zeros. */
    fun format(value: BigDecimal): String {
        val rounded = value.roundSignificand(DecimalMode(SHOWN_DIGITS.toLong(), RoundingMode.ROUND_HALF_TO_EVEN))
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }

    /** Accepts how people (and models) write maths: "×", "÷", "−", "1,000", "**". */
    private fun normalize(raw: String): String = raw
        .replace("×", "*")
        .replace("÷", "/")
        .replace("−", "-")
        .replace("**", "^")
        .replace(",", "")
        .lowercase()

    private class Parser(private val text: String) {
        private var position = 0

        fun sum(): BigDecimal {
            var value = product()
            while (true) {
                value = when {
                    take('+') -> value.add(product())
                    take('-') -> value.subtract(product())
                    else -> return value
                }
            }
        }

        private fun product(): BigDecimal {
            var value = unary()
            while (true) {
                value = when {
                    take('*') -> value.multiply(unary())
                    take('/') -> divide(value, unary())
                    else -> return value
                }
            }
        }

        private fun unary(): BigDecimal = when {
            take('-') -> unary().negate()
            take('+') -> unary()
            else -> power()
        }

        private fun power(): BigDecimal {
            val base = postfix()
            return if (take('^')) raise(base, unary()) else base
        }

        private fun postfix(): BigDecimal {
            val value = primary()
            return if (take('%')) value.moveDecimalPoint(PERCENT_SHIFT) else value
        }

        private fun primary(): BigDecimal {
            skipSpaces()
            val start = position
            return when {
                take('(') -> sum().also { expect(')') }
                peek()?.let { it.isDigit() || it == '.' } == true -> number(start)
                peek()?.isLetter() == true -> named()
                else -> throw ToolArgumentException("expected a number at position ${start + 1}")
            }
        }

        private fun number(start: Int): BigDecimal {
            while (peek()?.let { it.isDigit() || it == '.' } == true) position++
            // Scientific notation: 1.5e3 (an "e" followed by a digit or sign; otherwise it is the constant e)
            if (peek() == 'e' && text.getOrNull(position + 1)?.let(::startsExponent) == true) {
                position += 2
                while (peek()?.isDigit() == true) position++
            }
            val literal = text.substring(start, position)
            return literal.toDecimalOrNull() ?: throw ToolArgumentException("'$literal' is not a number")
        }

        private fun named(): BigDecimal {
            val start = position
            while (peek()?.isLetterOrDigit() == true) position++
            val word = text.substring(start, position)
            CONSTANTS[word]?.let { return it }
            val function = FUNCTIONS[word] ?: throw ToolArgumentException("unknown name '$word'")
            expect('(')
            val argument = sum()
            expect(')')
            return function(argument)
        }

        fun expectEnd() {
            skipSpaces()
            if (position < text.length) {
                throw ToolArgumentException("unexpected '${text[position]}' at position ${position + 1}")
            }
        }

        private fun expect(char: Char) {
            if (!take(char)) throw ToolArgumentException("expected '$char' at position ${position + 1}")
        }

        private fun take(char: Char): Boolean {
            skipSpaces()
            return (peek() == char).also { if (it) position++ }
        }

        private fun peek(): Char? = text.getOrNull(position)

        private fun startsExponent(char: Char) = char.isDigit() || char == '-' || char == '+'

        private fun skipSpaces() {
            while (peek()?.isWhitespace() == true) position++
        }
    }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw ArithmeticException("division by zero")
        return a.divide(b, CONTEXT)
    }

    /** Whole exponents stay exact; others (2^0.5) go through double precision. */
    private fun raise(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        val whole = exponent.isWholeNumber()
        return when {
            whole && exponent.abs() <= MAX_EXACT_EXPONENT -> {
                val n = exponent.intValue(exactRequired = true)
                if (n >= 0) exactPower(base, n) else divide(BigDecimal.ONE, exactPower(base, -n))
            }

            else -> real(base.toDouble().pow(exponent.toDouble()))
        }
    }

    /** base^n with whole n, rounded to [CONTEXT] like v2's `BigDecimal.pow(n, MathContext.DECIMAL128)`. */
    private fun exactPower(base: BigDecimal, n: Int): BigDecimal = base.pow(n.toLong()).roundSignificand(CONTEXT)

    private val MAX_EXACT_EXPONENT = BigDecimal.fromInt(999)

    /** A double result back to BigDecimal; NaN and infinity become a readable error. */
    private fun real(value: Double): BigDecimal {
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("the result is not a real number")
        return BigDecimal.fromDouble(value)
    }

    private val CONSTANTS = mapOf(
        "pi" to BigDecimal.parseString("3.14159265358979323846264338327950288"),
        "e" to BigDecimal.parseString("2.71828182845904523536028747135266250"),
    )

    private val FUNCTIONS: Map<String, (BigDecimal) -> BigDecimal> = mapOf(
        "sqrt" to { x ->
            if (x.signum() < 0) throw ArithmeticException("square root of a negative number")
            real(sqrt(x.toDouble()))
        },
        "abs" to { x -> x.abs() },
        "round" to { x -> x.roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO) },
        "floor" to { x -> x.floor() },
        "ceil" to { x -> x.ceil() },
        "ln" to { x -> positive(x) { real(ln(it)) } },
        "log" to { x -> positive(x) { real(log10(it)) } },
        "sin" to { x -> real(sin(x.toDouble())) },
        "cos" to { x -> real(cos(x.toDouble())) },
        "tan" to { x -> real(tan(x.toDouble())) },
    )

    private fun positive(x: BigDecimal, f: (Double) -> BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw ArithmeticException("logarithm of a number that is not positive")
        return f(x.toDouble())
    }
}
