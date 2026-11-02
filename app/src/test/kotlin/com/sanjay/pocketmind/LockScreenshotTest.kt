package com.sanjay.pocketmind

import com.sanjay.pocketmind.core.testing.captureScreenshots
import com.sanjay.pocketmind.ui.LockScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the app lock overlay. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockScreenshotTest {
    @Test
    fun locked() = captureScreenshots("lock") { LockScreen(onUnlock = {}) }
}
