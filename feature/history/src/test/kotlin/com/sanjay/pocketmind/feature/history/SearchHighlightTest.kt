package com.sanjay.pocketmind.feature.history

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHighlightTest {
    @Test
    fun `matches word starts case-insensitively`() {
        // "Dronebot drone": D=0 … space=8, d=9
        assertEquals(listOf(0..2, 9..11), searchMatches("Dronebot drone", "dro"))
        assertEquals(emptyList<IntRange>(), searchMatches("hydro", "dro")) // not a word start
    }

    @Test
    fun `several words are all marked and overlaps merge`() {
        assertEquals(listOf(0..2, 6..8), searchMatches("Smart drone", "sma dro"))
        assertEquals(listOf(0..4), searchMatches("drone", "dr drone"))
    }

    @Test
    fun `an empty query marks nothing and punctuation in the query is ignored`() {
        assertEquals(emptyList<IntRange>(), searchMatches("anything", "  "))
        // "(c)" is reduced to the word "c", like the search itself.
        assertEquals(listOf(4..4), searchMatches("is (c) here", "(c)"))
    }

    @Test
    fun `Hindi words are matched whole`() {
        assertEquals(listOf(0..5), searchMatches("नमस्ते दुनिया", "नमस्ते"))
    }
}
