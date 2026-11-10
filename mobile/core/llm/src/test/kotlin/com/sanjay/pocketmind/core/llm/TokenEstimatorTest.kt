package com.sanjay.pocketmind.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {
    @Test
    fun `empty text is zero tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `english is about four characters per token, rounded up`() {
        assertEquals(1, TokenEstimator.estimate("Hi"))
        assertEquals(3, TokenEstimator.estimate("Hello world!")) // 12 chars → 3
    }

    @Test
    fun `hindi costs more tokens per character than english`() {
        val hindi = "नमस्ते दुनिया" // 13 characters
        assertTrue(TokenEstimator.estimate(hindi) > TokenEstimator.estimate("a".repeat(13)))
    }
}
