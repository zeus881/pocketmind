package com.sanjay.pocketmind.core.llm

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Answers one question: "did the previous run of this app end in a crash?"
 *
 * WHY an interface: [LoadGuard] needs the answer, but the real source is an Android system
 * service. Behind an interface, unit tests can say "yes", "no" or "unknown" without a phone.
 */
fun interface ExitReasonSource {
    /** true = crashed, false = ended some other way (user swipe, low memory…), null = unknown. */
    fun previousProcessCrashed(): Boolean?
}

/**
 * Real implementation using `ActivityManager.getHistoricalProcessExitReasons` (Android 11+).
 * Android records WHY each of our processes died: a Java crash, a native crash (SIGSEGV in
 * the GPU driver, say), an ANR, a low-memory kill, the user swiping it away, and so on.
 * Before Android 11 there is no such record, so the answer is "unknown".
 */
internal class AndroidExitReasonSource @Inject constructor(@ApplicationContext private val context: Context) :
    ExitReasonSource {
    override fun previousProcessCrashed(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        // pid 0 = any of our processes; maxNum 1 = only the most recent exit.
        val last = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
            ?.firstOrNull()
        return last?.let {
            it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_CRASH
        }
    }
}
