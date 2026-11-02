package com.sanjay.pocketmind

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressureTest {
    @Test
    fun `unloads on critical pressure and deep in the kill list`() {
        assertTrue(MemoryPressure.shouldUnloadModel(15)) // TRIM_MEMORY_RUNNING_CRITICAL
        assertTrue(MemoryPressure.shouldUnloadModel(60)) // TRIM_MEMORY_MODERATE
        assertTrue(MemoryPressure.shouldUnloadModel(80)) // TRIM_MEMORY_COMPLETE
    }

    @Test
    fun `ordinary backgrounding is left to the keep-alive timer`() {
        // Android 14+ sends these on every app switch: not memory pressure.
        assertFalse(MemoryPressure.shouldUnloadModel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(MemoryPressure.shouldUnloadModel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertFalse(MemoryPressure.shouldUnloadModel(5)) // TRIM_MEMORY_RUNNING_MODERATE
    }
}
