package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Writes real files (file:// URIs stand in for the "Save as" picker's content:// URIs).
 * PDF writing needs the device's native PdfDocument (Robolectric has none): see
 * src/androidTest/.../AndroidChatExporterPdfTest.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidChatExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter = AndroidChatExporter(context, Dispatchers.Unconfined)
    private val messages = List(60) { i ->
        Message(
            "$i",
            "c",
            if (i % 2 ==
                0
            ) {
                TurnRole.USER
            } else {
                TurnRole.ASSISTANT
            },
            "Line $i " + "text ".repeat(30),
            i.toLong(),
        )
    }

    @Test
    fun `markdown is written as UTF-8 text`() = runTest {
        val file = tmp.newFile("chat.md")

        val result = exporter.writeMarkdown(Uri.fromFile(file).toString(), "नमस्ते", messages.take(2))

        assertEquals(AppResult.Success(Unit), result)
        assertTrue(file.readText().startsWith("# नमस्ते"))
    }

    @Test
    fun `an unwritable location is reported, not thrown`() = runTest {
        val missingDir = File(tmp.root, "does/not/exist/chat.md")

        val result = exporter.writeMarkdown(Uri.fromFile(missingDir).toString(), "x", messages.take(1))

        assertTrue(result is AppResult.Failure)
    }
}
