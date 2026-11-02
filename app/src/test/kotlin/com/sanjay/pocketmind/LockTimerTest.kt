package com.sanjay.pocketmind

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimerTest {
    private val timer = LockTimer()
    private val timeout = 30_000L

    @Test
    fun `a short trip away does not lock`() {
        timer.onBackground(nowMs = 1_000, isLocked = false)
        assertFalse(timer.shouldLockOnForeground(nowMs = 20_000, enabled = true, timeoutMs = timeout))
    }

    @Test
    fun `staying away for the timeout locks`() {
        timer.onBackground(nowMs = 1_000, isLocked = false)
        assertTrue(timer.shouldLockOnForeground(nowMs = 31_000, enabled = true, timeoutMs = timeout))
    }

    @Test
    fun `timeout zero locks immediately`() {
        timer.onBackground(nowMs = 1_000, isLocked = false)
        assertTrue(timer.shouldLockOnForeground(nowMs = 1_000, enabled = true, timeoutMs = 0))
    }

    @Test
    fun `a disabled lock never locks`() {
        timer.onBackground(nowMs = 0, isLocked = false)
        assertFalse(timer.shouldLockOnForeground(nowMs = 999_999, enabled = false, timeoutMs = 0))
    }

    @Test
    fun `coming to the foreground without leaving first does not lock`() {
        // onStart at the very first launch.
        assertFalse(timer.shouldLockOnForeground(nowMs = 50_000, enabled = true, timeoutMs = 0))
    }

    @Test
    fun `our own picker gets a long grace period`() {
        timer.onLaunchingForResult()
        timer.onBackground(nowMs = 0, isLocked = false)
        // Two minutes choosing a PDF: longer than the 30 s timeout, but it was our picker.
        assertFalse(timer.shouldLockOnForeground(nowMs = 120_000, enabled = true, timeoutMs = timeout))
    }

    @Test
    fun `the picker grace period also ends`() {
        timer.onLaunchingForResult()
        timer.onBackground(nowMs = 0, isLocked = false)
        assertTrue(
            timer.shouldLockOnForeground(nowMs = LockTimer.EXTERNAL_GRACE_MS, enabled = true, timeoutMs = timeout),
        )
    }

    @Test
    fun `the picker exemption is used up by one trip`() {
        timer.onLaunchingForResult()
        timer.onBackground(nowMs = 0, isLocked = false)
        timer.shouldLockOnForeground(nowMs = 1_000, enabled = true, timeoutMs = timeout)

        timer.onBackground(nowMs = 2_000, isLocked = false) // a normal trip to the home screen
        assertTrue(timer.shouldLockOnForeground(nowMs = 40_000, enabled = true, timeoutMs = timeout))
    }

    @Test
    fun `leaving while already locked records nothing`() {
        // The system PIN screen of the unlock dialog sends us to the background while locked.
        timer.onLaunchingForResult()
        timer.onBackground(nowMs = 0, isLocked = true)
        assertFalse(timer.shouldLockOnForeground(nowMs = 999_999, enabled = true, timeoutMs = 0))

        // …and the stale "launching for result" flag must not exempt the next real trip.
        timer.onBackground(nowMs = 1_000_000, isLocked = false)
        assertTrue(timer.shouldLockOnForeground(nowMs = 1_040_000, enabled = true, timeoutMs = timeout))
    }
}
