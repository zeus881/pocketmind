package com.sanjay.pocketmind.data.chat

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PDF export on a real phone (Android's PdfDocument is native code; Robolectric has none).
 * Run: `./gradlew :data:chat:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class AndroidChatExporterPdfTest {
    @Test
    fun aLongChatBecomesAMultiPagePdf() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "chat-export-test.pdf").apply { delete() }
        val messages = List(60) { i ->
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

        val result = AndroidChatExporter(context, Dispatchers.IO).writePdf(Uri.fromFile(file), "Long chat", messages)

        assertEquals(AppResult.Success(Unit), result)
        val bytes = file.readBytes()
        assertEquals("%PDF-", String(bytes, 0, 5))
        assertTrue(Regex("/Type\\s*/Page[^s]").findAll(String(bytes, Charsets.ISO_8859_1)).count() > 1)
    }
}
