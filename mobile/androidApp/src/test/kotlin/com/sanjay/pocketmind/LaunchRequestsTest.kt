package com.sanjay.pocketmind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchRequestsTest {
    private val send = "android.intent.action.SEND"

    @Test
    fun `widget and tile actions`() {
        assertEquals(LaunchRequest.NewChat, LaunchRequests.parse(LaunchRequests.ACTION_NEW_CHAT, null))
        assertEquals(
            LaunchRequest.OpenChat("c1"),
            LaunchRequests.parse(LaunchRequests.ACTION_OPEN_CHAT, null, chatId = "c1"),
        )
        assertNull(LaunchRequests.parse(LaunchRequests.ACTION_OPEN_CHAT, null, chatId = " "))
    }

    @Test
    fun `shared text joins subject and body and is trimmed`() {
        assertEquals(
            LaunchRequest.ShareText("Article title\n\nThe body."),
            LaunchRequests.parse(send, "text/plain", text = " The body. ", subject = "Article title"),
        )
        // Many apps put the same text in both extras.
        assertEquals(
            LaunchRequest.ShareText("Same"),
            LaunchRequests.parse(send, "text/plain", text = "Same", subject = "Same"),
        )
        assertNull(LaunchRequests.parse(send, "text/plain", text = "   "))
    }

    @Test
    fun `very long shared text is capped`() {
        val request = LaunchRequests.parse(send, "text/plain", text = "x".repeat(20_000)) as LaunchRequest.ShareText
        assertEquals(LaunchRequests.MAX_SHARED_TEXT, request.text.length)
    }

    @Test
    fun `a shared PDF needs its stream uri`() {
        assertEquals(
            LaunchRequest.SharePdf("content://docs/1"),
            LaunchRequests.parse(send, "application/pdf", streamUri = "content://docs/1"),
        )
        assertNull(LaunchRequests.parse(send, "application/pdf"))
    }

    @Test
    fun `everything else opens the app normally`() {
        assertNull(LaunchRequests.parse("android.intent.action.MAIN", null))
        assertNull(LaunchRequests.parse(send, "image/png", streamUri = "content://x"))
        assertNull(LaunchRequests.parse(null, null))
    }
}
