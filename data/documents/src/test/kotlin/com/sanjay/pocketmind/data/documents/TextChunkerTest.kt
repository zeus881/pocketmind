package com.sanjay.pocketmind.data.documents

import com.sanjay.pocketmind.core.llm.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    private fun sentence(i: Int) = "Sentence number $i talks about drones and batteries."

    private fun page(n: Int, sentences: IntRange) = PageText(n, sentences.joinToString(" ") { sentence(it) })

    @Test
    fun shortTextIsOneChunk() {
        val chunks = TextChunker.chunk(listOf(page(1, 1..3)), maxTokens = 500, overlapTokens = 50)
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].startPage)
    }

    @Test
    fun everyChunkRespectsTheTokenLimit() {
        val pages = (1..10).map { page(it, (it * 10)..(it * 10 + 9)) }
        val chunks = TextChunker.chunk(pages, maxTokens = 120, overlapTokens = 12)
        assertTrue(chunks.size > 5)
        // +1 per sentence for the joining space; the chunk text itself must fit.
        chunks.forEach { assertTrue("chunk ${it.index} too long", TokenEstimator.estimate(it.text) <= 120) }
    }

    @Test
    fun neighbouringChunksOverlap() {
        val pages = listOf(page(1, 1..40))
        val chunks = TextChunker.chunk(pages, maxTokens = 100, overlapTokens = 20)
        for (i in 1 until chunks.size) {
            val lastSentenceOfPrevious = chunks[i - 1].text.substringAfterLast("Sentence")
            assertTrue("chunk $i must start with overlap", chunks[i].text.contains(lastSentenceOfPrevious))
        }
    }

    @Test
    fun noTextIsLostAndPageRangesAreKept() {
        val pages = (1..5).map { page(it, (it * 10)..(it * 10 + 4)) }
        val chunks = TextChunker.chunk(pages, maxTokens = 60, overlapTokens = 0)
        val joined = chunks.joinToString(" ") { it.text }
        (10..54).filter { it % 10 < 5 }.forEach { assertTrue("missing $it", joined.contains("number $it ")) }
        assertEquals(1, chunks.first().startPage)
        assertEquals(5, chunks.last().endPage)
        chunks.forEach { assertTrue(it.startPage <= it.endPage) }
    }

    @Test
    fun aHugeSentenceIsSplitByWords() {
        val giant = PageText(1, (1..500).joinToString(" ") { "word$it" })
        val chunks = TextChunker.chunk(listOf(giant), maxTokens = 50, overlapTokens = 0)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(TokenEstimator.estimate(it.text) <= 52) }
    }

    @Test
    fun emptyPagesGiveNoChunks() {
        assertEquals(emptyList<TextChunk>(), TextChunker.chunk(listOf(PageText(1, "   ")), 100, 10))
    }
}
