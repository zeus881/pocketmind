package com.sanjay.pocketmind.core.tools

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

// Exact decimal maths for the tools on every platform (V3).
//
// v2 used `java.math.BigDecimal`, which iOS (Kotlin/Native) doesn't have. The KMP library
// `com.ionspin.kotlin:bignum` (Apache-2.0; API read from its 0.3.10 JVM jar) gives the same decimal model:
// an arbitrary-precision significand and an exponent, with an explicit precision and rounding per division.

/** 34 significant digits, round half to even: the same as Java's `MathContext.DECIMAL128` used in v2. */
internal val DECIMAL128 = DecimalMode(decimalPrecision = 34, roundingMode = RoundingMode.ROUND_HALF_TO_EVEN)

/** A decimal literal such as "12.5" or "1.5e3", or null if [this] is not a number. */
internal fun String.toDecimalOrNull(): BigDecimal? =
    if (DECIMAL_LITERAL.matches(this)) BigDecimal.parseString(this) else null

/** Digits with an optional point and an optional exponent; what the parser and the JSON arguments may contain. */
private val DECIMAL_LITERAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/** For the functions that go through double precision (sqrt, ln, sin, …), as v2's `BigDecimal.toDouble()` did. */
internal fun BigDecimal.toDouble(): Double = doubleValue(exactRequired = false)
