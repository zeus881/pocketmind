package com.sanjay.pocketmind

/**
 * Decides WHEN the app lock comes back. Pure logic with explicit timestamps, so every rule is
 * unit-tested without an Activity or a clock.
 *
 * Rules:
 * 1. Leaving the app starts a timer; returning after [timeoutMs] or more locks it.
 * 2. Leaving because WE opened another screen for a result (the system file picker for a PDF,
 *    the photo picker) doesn't count, as long as the user returns within [EXTERNAL_GRACE_MS].
 *    Without this, picking a PDF would lock the app and lose the picked file.
 * 3. Leaving while already locked records nothing. The PIN screen of the system unlock dialog
 *    sends us to the background too; it must not re-lock the app right after a successful unlock.
 */
internal class LockTimer {
    private var backgroundAtMs: Long? = null
    private var leftForResult = false
    private var expectingResult = false

    /** We are about to start another activity and wait for its result (pickers). */
    fun onLaunchingForResult() {
        expectingResult = true
    }

    /** The app left the foreground at [nowMs]. [isLocked] = the lock is already showing. */
    fun onBackground(nowMs: Long, isLocked: Boolean) {
        if (isLocked) {
            backgroundAtMs = null
            expectingResult = false // e.g. the unlock dialog's own PIN screen
            return
        }
        backgroundAtMs = nowMs
        leftForResult = expectingResult
        expectingResult = false
    }

    /** The app came back at [nowMs]. Returns true if it must lock now. */
    fun shouldLockOnForeground(nowMs: Long, enabled: Boolean, timeoutMs: Long): Boolean {
        val leftAt = backgroundAtMs ?: return false
        backgroundAtMs = null
        val away = nowMs - leftAt
        val graceMs = if (leftForResult) maxOf(timeoutMs, EXTERNAL_GRACE_MS) else timeoutMs
        leftForResult = false
        return enabled && away >= graceMs
    }

    companion object {
        /** Time allowed in one of our own pickers before the lock applies anyway. */
        const val EXTERNAL_GRACE_MS = 10 * 60 * 1000L
    }
}
