package com.sanjay.pocketmind.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException

class AppResultTest {
    @Test
    fun successWrapsTheValue() {
        val result = runCatchingApp(ErrorKind.UNKNOWN) { 42 }
        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun exceptionBecomesFailureWithTheGivenKind() {
        val result = runCatchingApp(ErrorKind.FILE_UNREADABLE) { error("boom") }
        assertEquals(ErrorKind.FILE_UNREADABLE, assertIs<AppResult.Failure>(result).kind)
    }

    @Test
    fun cancellationIsRethrownNeverSwallowed() {
        assertFailsWith<CancellationException> {
            runCatchingApp(ErrorKind.UNKNOWN) { throw CancellationException("cancelled") }
        }
    }
}
