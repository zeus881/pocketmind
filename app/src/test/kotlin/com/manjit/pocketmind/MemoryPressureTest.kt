package com.manjit.pocketmind

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressureTest {
    @Test
    fun `unloads on critical and background levels`() {
        assertTrue(MemoryPressure.shouldUnloadModel(15)) // TRIM_MEMORY_RUNNING_CRITICAL
        assertTrue(MemoryPressure.shouldUnloadModel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(MemoryPressure.shouldUnloadModel(80)) // TRIM_MEMORY_COMPLETE
    }

    @Test
    fun `keeps the model when only the UI is hidden or memory is moderately low`() {
        assertFalse(MemoryPressure.shouldUnloadModel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(MemoryPressure.shouldUnloadModel(5)) // TRIM_MEMORY_RUNNING_MODERATE
    }
}
