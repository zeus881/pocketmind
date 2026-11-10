package com.sanjay.pocketmind.core.common.time

/**
 * Wall-clock time in milliseconds, behind an interface.
 *
 * WHY: code that calls the system clock directly can't be tested for things like "messages are ordered by
 * time" — every run gives different values. Tests inject a fake clock.
 *
 * V3: shared (Android + iOS). [SYSTEM] reads the real clock through `kotlin.time`, which works on both.
 */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        /** The device's real wall clock. */
        val SYSTEM: Clock = Clock { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }
}
