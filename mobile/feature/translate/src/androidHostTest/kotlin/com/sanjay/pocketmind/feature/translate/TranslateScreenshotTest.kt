package com.sanjay.pocketmind.feature.translate

import com.sanjay.pocketmind.core.testing.captureScreenshots
import com.sanjay.pocketmind.data.documents.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the translator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslateScreenshotTest {
    private val noActions = object : TranslateActions {
        override fun onInputChange(text: String) = Unit
        override fun setFrom(language: Language) = Unit
        override fun setTo(language: Language) = Unit
        override fun swap() = Unit
        override fun translate() = Unit
        override fun stop() = Unit
    }

    @Test
    fun translated() = captureScreenshots("translate") {
        TranslateContent(
            state = TranslateUiState(input = "Where is the railway station?", output = "रेलवे स्टेशन कहाँ है?"),
            vm = noActions,
        )
    }
}
