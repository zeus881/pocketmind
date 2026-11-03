package com.sanjay.pocketmind.e2e

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.MainActivity
import com.sanjay.pocketmind.R as AppR
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.feature.chat.R as ChatR
import com.sanjay.pocketmind.feature.documents.R as DocumentsR
import com.sanjay.pocketmind.feature.history.R as HistoryR
import com.sanjay.pocketmind.feature.models.R as ModelsR
import com.sanjay.pocketmind.feature.onboarding.R as OnboardingR
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The whole app, as a user goes through it: real MainActivity, navigation, screens, ViewModels,
 * repositories and Room; only what needs a phone or the network is replaced (TestModules.kt).
 * Runs on the JVM with Robolectric, so CI needs no emulator.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// A normal phone screen (Robolectric's default is 320 x 470 dp, where lists run off-screen).
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var engine: FakeLlmEngine

    @Inject lateinit var settings: SettingsDataSource

    @Inject lateinit var models: FakeModels

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hilt.inject()
        // As if the user already allowed notifications: Robolectric's permission dialog never answers.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    /** Waits for [nodes]; on timeout the failure shows what WAS on screen (the semantics tree). */
    private fun ComposeTestRule.waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        try {
            waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Not found. Screen:\n" + onRoot(useUnmergedTree = true).printToString(), e)
        }
    }

    private fun ComposeTestRule.waitForText(value: String, substring: Boolean = false) =
        waitFor { onAllNodesWithText(value, substring = substring) }

    private fun ComposeTestRule.onAllNodesWithText(value: String, substring: Boolean = false) =
        onAllNodes(androidx.compose.ui.test.hasText(value, substring = substring))

    /** Scrolls the element into view when it is in a list, then clicks it like a finger would. */
    private fun SemanticsNodeInteraction.tap() {
        runCatching { performScrollTo() } // only possible inside a scrolling list; elsewhere it is visible anyway
        performClick()
    }

    private fun ComposeTestRule.clickText(value: String) = onAllNodesWithText(value).onFirst().tap()

    private fun ComposeTestRule.clickDescription(value: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(value)).onFirst().tap()

    @Test
    fun `first run - onboarding, download, chat, history, delete and undo`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Onboarding: Next until "I understand — get started".
            val getStarted = text(OnboardingR.string.feature_onboarding_get_started)
            repeat(ONBOARDING_PAGES) {
                if (compose.onAllNodesWithText(getStarted).fetchSemanticsNodes().isEmpty()) {
                    compose.clickText(text(OnboardingR.string.feature_onboarding_next))
                }
            }
            compose.clickText(getStarted)

            // The model manager opens on top of the chat: download (instant here) and go back.
            compose.waitForText(text(ModelsR.string.feature_models_download))
            compose.clickText(text(ModelsR.string.feature_models_download))
            compose.waitForText(text(ModelsR.string.feature_models_in_use))
            assertEquals(1, models.downloadCalls)
            compose.clickDescription(text(ModelsR.string.feature_models_back))

            // Chat: the screen must notice the new model (the bug this test found), then answer.
            compose.waitFor { compose.onAllNodes(hasSetTextAction()) }
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("How do drones fly?")
            compose.clickDescription(text(ChatR.string.feature_chat_send))
            compose.waitForText("Drones fly with four rotors.", substring = true)
            assertTrue(engine.prompts.single() == "How do drones fly?")

            // History: the chat is listed with its automatic title; delete it, then undo.
            compose.clickText(text(AppR.string.nav_history))
            val title = "How do drones fly"
            compose.waitForText(title)
            compose.clickDescription(text(HistoryR.string.feature_history_more, title))
            compose.clickText(text(HistoryR.string.feature_history_delete))
            compose.waitForText(text(HistoryR.string.feature_history_undo))
            compose.clickText(text(HistoryR.string.feature_history_undo))
            compose.waitForText(title)
            scenario.onActivity { } // still running: nothing crashed on the way
        }
    }

    /** A one-page PDF with a line of text, written by PdfBox. */
    private fun pdfWithText(): Uri {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "shared.pdf")
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                stream.newLineAtOffset(MARGIN, MARGIN)
                stream.showText("Drones are aircraft without a pilot on board.")
                stream.endText()
            }
            document.save(file)
        }
        return Uri.fromFile(file)
    }

    private fun readyToChat() = runBlocking {
        settings.setOnboardingDone(true)
        models.download(models.model.id)
    }

    @Test
    fun `a PDF shared from another app is summarized on the phone`() {
        readyToChat()
        val share = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, pdfWithText())

        ActivityScenario.launch<MainActivity>(share).use {
            compose.waitForText("Drones fly with four rotors.", substring = true)
            val copy = text(DocumentsR.string.feature_documents_copy)
            compose.waitFor { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(copy)) }
        }
    }

    @Test
    fun `with app lock on, the lock screen covers the app at start`() {
        readyToChat()
        runBlocking { settings.setAppLockEnabled(true) }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText(text(AppR.string.lock_title))
            compose.waitForText(text(AppR.string.lock_unlock))
            // The chat underneath is hidden from touch and from TalkBack while locked.
            assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val ONBOARDING_PAGES = 4
        const val FONT_SIZE = 12f
        const val MARGIN = 72f
    }
}
