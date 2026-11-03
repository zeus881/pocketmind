package com.sanjay.pocketmind.core.common.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The phone facts in a diagnostic report, read from Robolectric's simulated device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceFactsTest {
    @Test
    fun `facts come from the system, with the API level and a device name`() {
        val facts = AndroidDeviceFacts(ApplicationProvider.getApplicationContext<Context>()).facts()

        assertEquals(34, facts.sdk)
        assertTrue(facts.device.isNotBlank())
        assertTrue(facts.totalRamGb >= 0)
    }

    @Test
    fun `installing the crash log keeps the previous handler`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var handledBefore = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> handledBefore = true }
        val log = CrashLog(context) { 0L }.apply { clear() }

        log.install()
        val crash = IllegalStateException("x")
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), crash)

        assertTrue(handledBefore) // Android's own handling (the crash dialog) still happens
        assertEquals(1, log.entries().size)
        Thread.setDefaultUncaughtExceptionHandler(original)
        log.clear()
    }
}
