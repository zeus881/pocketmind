package com.sanjay.pocketmind.core.common.format

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Fixed-point text for numbers shown to people and in shared reports: "26.3", "0.39", "5454".
 *
 * WHY: v2 used `String.format(Locale.US, "%.1f", …)`, which is JVM-only; iOS (Kotlin/Native) has no
 * `String.format`. This gives the same text for the values PocketMind shows (speeds, seconds, sizes):
 * rounded to the nearest, ties away from zero, always [decimals] digits after a '.', never a
 * locale-specific separator (reports are shared as plain text).
 */
fun Double.formatFixed(decimals: Int): String {
    require(decimals in 0..MAX_DECIMALS) { "decimals must be 0..$MAX_DECIMALS, got $decimals" }
    if (isNaN() || isInfinite()) return toString()
    val factor = POWERS_OF_TEN[decimals]
    val scaled = (abs(this) * factor).roundToLong()
    val whole = scaled / factor
    val fraction = scaled % factor
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return if (decimals == 0) {
        "$sign$whole"
    } else {
        "$sign$whole." + fraction.toString().padStart(decimals, '0')
    }
}

/** Two-digit clock fields: 7 → "07". */
fun Int.twoDigits(): String = toString().padStart(2, '0')

private const val MAX_DECIMALS = 9
private val POWERS_OF_TEN = LongArray(MAX_DECIMALS + 1).also { powers ->
    powers[0] = 1
    for (i in 1..MAX_DECIMALS) powers[i] = powers[i - 1] * 10
}
