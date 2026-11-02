package com.sanjay.pocketmind.core.llm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The crash-loop guard. A "process death" is simulated by creating a NEW guard over the same
 * folder without calling end(): exactly what a real native crash leaves on disk.
 */
class LoadGuardTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val path = "/models/gemma/model.litertlm"

    private fun guard(dir: File, crashed: Boolean?) = LoadGuard.forTesting(dir) { crashed }

    @Test
    fun `a normal load leaves nothing behind`() {
        val dir = tmp.newFolder()
        guard(dir, crashed = true).apply {
            recoverFromPreviousRun()
            begin(path, LlmBackend.GPU)
            end()
        }

        val nextRun = guard(dir, crashed = true).apply { recoverFromPreviousRun() }

        assertEquals(emptySet<LlmBackend>(), nextRun.blockedBackends(path))
    }

    @Test
    fun `a native crash during GPU load blocks GPU on the next start`() {
        val dir = tmp.newFolder()
        guard(dir, crashed = true).begin(path, LlmBackend.GPU) // process dies here: no end()

        val nextRun = guard(dir, crashed = true).apply { recoverFromPreviousRun() }

        assertEquals(setOf(LlmBackend.GPU), nextRun.blockedBackends(path))
        assertEquals(emptySet<LlmBackend>(), nextRun.blockedBackends("/models/other.litertlm"))
    }

    @Test
    fun `a non-crash exit during load blocks nothing`() {
        val dir = tmp.newFolder()
        guard(dir, crashed = false).begin(path, LlmBackend.GPU) // e.g. the user swiped the app away

        val nextRun = guard(dir, crashed = false).apply { recoverFromPreviousRun() }

        assertEquals(emptySet<LlmBackend>(), nextRun.blockedBackends(path))
    }

    @Test
    fun `an unknown exit reason is treated as a crash`() {
        val dir = tmp.newFolder()
        guard(dir, crashed = null).begin(path, LlmBackend.CPU)

        val nextRun = guard(dir, crashed = null).apply { recoverFromPreviousRun() }

        assertEquals(setOf(LlmBackend.CPU), nextRun.blockedBackends(path))
    }

    @Test
    fun `crashes on both backends accumulate and survive restarts`() {
        val dir = tmp.newFolder()
        guard(dir, crashed = true).begin(path, LlmBackend.GPU)
        guard(dir, crashed = true).apply {
            recoverFromPreviousRun()
            begin(path, LlmBackend.CPU)
        }

        val third = guard(dir, crashed = true).apply { recoverFromPreviousRun() }

        assertEquals(setOf(LlmBackend.GPU, LlmBackend.CPU), third.blockedBackends(path))
    }

    @Test
    fun `recovery runs only once per process`() {
        val dir = tmp.newFolder()
        val guard = guard(dir, crashed = true)
        guard.recoverFromPreviousRun()
        guard.begin(path, LlmBackend.GPU) // still loading in THIS process

        guard.recoverFromPreviousRun() // a second call must not treat our own marker as a crash

        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(path))
    }

    @Test
    fun `clear forgets one model and clearAll forgets everything`() {
        val dir = tmp.newFolder()
        val other = "/models/other.litertlm"
        guard(dir, crashed = true).begin(path, LlmBackend.GPU)
        guard(dir, crashed = true).apply {
            recoverFromPreviousRun()
            begin(other, LlmBackend.GPU)
        }
        val guard = guard(dir, crashed = true).apply { recoverFromPreviousRun() }
        assertTrue(guard.blockedBackends(path).isNotEmpty() && guard.blockedBackends(other).isNotEmpty())

        guard.clear(path)
        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(path))
        assertEquals(setOf(LlmBackend.GPU), guard.blockedBackends(other))

        guard.clearAll()
        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(other))
    }

    @Test
    fun `a corrupt marker is ignored`() {
        val dir = tmp.newFolder()
        File(dir, "loading").writeText("NOT_A_BACKEND\n")

        val guard = guard(dir, crashed = true).apply { recoverFromPreviousRun() }

        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(path))
        assertEquals(false, File(dir, "loading").exists())
    }
}
