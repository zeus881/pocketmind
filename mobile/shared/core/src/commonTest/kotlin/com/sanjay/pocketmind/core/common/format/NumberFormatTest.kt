package com.sanjay.pocketmind.core.common.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Same text as v2's `String.format(Locale.US, "%.Nf", …)` for the values PocketMind shows; every platform. */
class NumberFormatTest {
    @Test
    fun roundsToTheRequestedDigits() {
        assertEquals("26.3", 26.3.formatFixed(1))
        assertEquals("0.39", 0.39.formatFixed(2))
        assertEquals("5.45", 5.454.formatFixed(2))
        assertEquals("13.30", 13.3.formatFixed(2))
        assertEquals("21.7", 21.66.formatFixed(1))
    }

    @Test
    fun wholeNumbersAndZero() {
        assertEquals("5454", 5454.0.formatFixed(0))
        assertEquals("0.0", 0.0.formatFixed(1))
        assertEquals("1.00", 0.999.formatFixed(2))
    }

    @Test
    fun negativesKeepTheirSignButNegativeZeroDoesNot() {
        assertEquals("-2.5", (-2.5).formatFixed(1))
        assertEquals("0.0", (-0.01).formatFixed(1))
    }

    @Test
    fun clockFieldsArePadded() {
        assertEquals("07", 7.twoDigits())
        assertEquals("14", 14.twoDigits())
    }

    @Test
    fun tooManyDecimalsIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { 1.0.formatFixed(10) }
    }
}
