package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.LlmBackend
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ModelVerifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val content = "pretend model weights".toByteArray()
    private val files by lazy { ModelFiles.forTesting(tmp.newFolder("models")) }
    private val guardDir by lazy { tmp.newFolder("guard") }
    private val guard by lazy { LoadGuard.forTesting(guardDir) { true } }

    private fun verifier(info: LlmModelInfo): ModelVerifier {
        val catalog = mockk<ModelCatalog> {
            every { find(any()) } returns null
            every { find(info.id) } returns info
        }
        return ModelVerifier(catalog, files, guard, UnconfinedTestDispatcher())
    }

    private fun info(size: Long = content.size.toLong(), sha: String = Sha256.ofBytes(content)) =
        LlmModelInfo("m", "Model", "", ModelKind.CHAT, "https://huggingface.co/x", size, sha, 4, "Apache-2.0")

    private fun writeModel(bytes: ByteArray = content) {
        files.finalFile("m").apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    @Test
    fun `an intact file passes and its crash records are cleared`() = runTest {
        writeModel()
        val path = files.finalFile("m").path
        guard.begin(path, LlmBackend.GPU) // simulate a crash record for this file
        LoadGuard.forTesting(guardDir) { true }.recoverFromPreviousRun()

        val result = verifier(info()).verify("m")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(path))
    }

    @Test
    fun `a file of the wrong size is reported damaged`() = runTest {
        writeModel(content.copyOf(content.size - 1))

        val result = verifier(info()).verify("m")

        assertEquals(ErrorKind.CHECKSUM_MISMATCH, (result as AppResult.Failure).kind)
    }

    @Test
    fun `a file with the right size but different bytes is reported damaged`() = runTest {
        val changed = content.copyOf().also { it[0] = (it[0] + 1).toByte() }
        writeModel(changed)

        val result = verifier(info()).verify("m")

        assertEquals(ErrorKind.CHECKSUM_MISMATCH, (result as AppResult.Failure).kind)
    }

    @Test
    fun `a model that is not downloaded is NO_MODEL and an unknown id is UNKNOWN`() = runTest {
        assertEquals(ErrorKind.NO_MODEL, (verifier(info()).verify("m") as AppResult.Failure).kind)
        assertEquals(ErrorKind.UNKNOWN, (verifier(info()).verify("nope") as AppResult.Failure).kind)
    }
}
