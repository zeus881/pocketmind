package com.sanjay.pocketmind.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `words become quoted prefix terms`() {
        assertEquals("\"drone*\" \"batt*\"", SearchQuery.from("  drone batt ").ftsQuery)
    }

    @Test
    fun `FTS syntax typed by the user is neutralised`() {
        assertEquals("\"a*\" \"b*\" \"OR*\" \"c*\"", SearchQuery.from("a* \"b\" OR -c").ftsQuery)
        assertEquals("", SearchQuery.from("*** \"\"").ftsQuery)
    }

    @Test
    fun `LIKE wildcards and the escape character are escaped`() {
        assertEquals("50\\% off\\_now \\\\", SearchQuery.from("50% off_now \\").likePattern)
    }

    @Test
    fun `non-Latin words are kept`() {
        assertEquals("\"नमस्ते*\"", SearchQuery.from("नमस्ते!").ftsQuery)
    }
}
