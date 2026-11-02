package com.sanjay.pocketmind.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.LaunchRequest
import com.sanjay.pocketmind.LaunchRequests
import com.sanjay.pocketmind.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Intents the widget and tile send are exactly what MainActivity understands. */
@RunWith(RobolectricTestRunner::class)
class WidgetIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun Intent.parse() = LaunchRequests.parse(
        action = action,
        mimeType = type,
        chatId = getStringExtra(LaunchRequests.EXTRA_CHAT_ID),
    )

    @Test
    fun `new chat intent opens MainActivity in a new task`() {
        val intent = launchIntent(context, LaunchRequests.ACTION_NEW_CHAT)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(LaunchRequest.NewChat, intent.parse())
    }

    @Test
    fun `open chat intents are distinct per chat`() {
        val a = openChatIntent(context, "a")
        val b = openChatIntent(context, "b")

        assertEquals(LaunchRequest.OpenChat("a"), a.parse())
        // Different data URIs → different PendingIntents (extras alone would not differ).
        assertNotEquals(a.data, b.data)
        assertTrue(!a.filterEquals(b))
    }
}
