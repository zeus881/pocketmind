package com.sanjay.pocketmind.core.common.result

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun `success wraps the value`() {
        val result = runCatchingApp(ErrorKind.UNKNOWN) { 42 }
        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun `exception becomes failure with the given kind`() {
        val result = runCatchingApp(ErrorKind.FILE_UNREADABLE) { error("boom") }
        assertTrue(result is AppResult.Failure)
        assertEquals(ErrorKind.FILE_UNREADABLE, (result as AppResult.Failure).kind)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, never swallowed`() {
        runCatchingApp(ErrorKind.UNKNOWN) { throw CancellationException("cancelled") }
    }
}
