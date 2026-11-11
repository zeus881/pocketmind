package com.sanjay.pocketmind.feature.personas

import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.testing.captureScreenshots
import com.sanjay.pocketmind.data.chat.BuiltInPersonas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonasScreenshotTest {
    @Test
    fun personasAndPrompts() = captureScreenshots("personas") {
        PersonasContent(
            state = PersonasUiState(
                isLoading = false,
                personas = BuiltInPersonas.all,
                prompts = listOf(SavedPrompt("1", "Polite email", "Write a polite email about")),
            ),
            onBack = {},
            actions = PersonasActions({ _, _, _, _ -> }, {}, { _, _, _ -> }, {}),
        )
    }
}
