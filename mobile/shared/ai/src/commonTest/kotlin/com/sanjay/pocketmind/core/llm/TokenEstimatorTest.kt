package com.sanjay.pocketmind.core.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenEstimatorTest {
    @Test
    fun emptyTextIsZeroTokens() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun englishIsAboutFourCharactersPerTokenRoundedUp() {
        assertEquals(1, TokenEstimator.estimate("Hi"))
        assertEquals(3, TokenEstimator.estimate("Hello world!")) // 12 chars → 3
    }

    @Test
    fun hindiCostsMoreTokensPerCharacterThanEnglish() {
        val hindi = "नमस्ते दुनिया" // 13 characters
        assertTrue(TokenEstimator.estimate(hindi) > TokenEstimator.estimate("a".repeat(13)))
    }
}
