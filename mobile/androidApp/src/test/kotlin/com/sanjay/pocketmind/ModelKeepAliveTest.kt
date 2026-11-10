package com.sanjay.pocketmind

import androidx.lifecycle.LifecycleOwner
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Virtual time: `advanceTimeBy` skips minutes instantly. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelKeepAliveTest {
    private val engine = FakeLlmEngine()
    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private val owner = mockk<LifecycleOwner>()
    private val minute = 60_000L

    @Test
    fun `the model is unloaded after the keep-alive time in the background`() = runTest {
        settings.setKeepModelLoadedMinutes(5)
        val keepAlive = ModelKeepAlive(engine, settings, backgroundScope)

        keepAlive.onStop(owner)
        advanceTimeBy(5 * minute - 1)
        runCurrent()
        assertEquals(0, engine.unloadCount)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, engine.unloadCount)
    }

    @Test
    fun `coming back in time keeps the model loaded`() = runTest {
        val keepAlive = ModelKeepAlive(engine, settings, backgroundScope) // default: 5 minutes

        keepAlive.onStop(owner)
        advanceTimeBy(2 * minute)
        keepAlive.onStart(owner)
        advanceTimeBy(10 * minute)
        runCurrent()

        assertEquals(0, engine.unloadCount)
    }

    @Test
    fun `zero minutes unloads right away`() = runTest {
        settings.setKeepModelLoadedMinutes(0)
        val keepAlive = ModelKeepAlive(engine, settings, backgroundScope)

        keepAlive.onStop(owner)
        runCurrent()

        assertEquals(1, engine.unloadCount)
    }

    @Test
    fun `leaving twice starts only one timer`() = runTest {
        settings.setKeepModelLoadedMinutes(5)
        val keepAlive = ModelKeepAlive(engine, settings, backgroundScope)

        keepAlive.onStop(owner)
        keepAlive.onStop(owner)
        advanceTimeBy(6 * minute)
        runCurrent()

        assertEquals(1, engine.unloadCount)
    }
}
