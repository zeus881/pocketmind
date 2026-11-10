package com.sanjay.pocketmind.core.llm

/**
 * Estimates how many tokens a text will use, WITHOUT running the model's tokenizer.
 *
 * WHY estimate? LiteRT-LM does not expose its tokenizer as a separate API, and we need
 * token counts *before* sending text (to trim chat history and to size PDF chunks).
 *
 * Rule of thumb: English averages ~4 characters per token. Non-Latin scripts (Hindi,
 * Chinese...) are split into many more tokens per character, so we count them at
 * ~2 characters per token. We deliberately round UP so we stay under the real limit.
 */
object TokenEstimator {
    private const val LATIN_CHARS_PER_TOKEN = 4
    private const val OTHER_CHARS_PER_TOKEN = 2
    private const val ASCII_LIMIT = 128

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var latin = 0
        var other = 0
        for (ch in text) {
            if (ch.code < ASCII_LIMIT) latin++ else other++
        }
        return ceilDiv(latin, LATIN_CHARS_PER_TOKEN) + ceilDiv(other, OTHER_CHARS_PER_TOKEN)
    }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
}
