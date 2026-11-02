package com.sanjay.pocketmind.feature.models

import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.captureScreenshots
import com.sanjay.pocketmind.data.models.ModelState
import com.sanjay.pocketmind.data.models.ModelStatus
import com.sanjay.pocketmind.feature.models.component.ModelActions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the model manager (record: `./gradlew :feature:models:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelsScreenshotTest {
    private fun info(id: String, name: String, size: Long, ram: Int, kind: ModelKind = ModelKind.CHAT) = LlmModelInfo(
        id, name, "On-device model for chat.", kind, "https://huggingface.co/x", size,
        "0".repeat(
            64,
        ),
        ram, "Apache-2.0",
    )

    private val actions = ModelActions({}, {}, {}, {}, {}, {})

    @Test
    fun catalog() = captureScreenshots("models_catalog") {
        ModelsContent(
            state = ModelsUiState(
                isLoading = false,
                chatModels = listOf(
                    ModelState(info("gemma", "Gemma 4 E2B", 2_588_147_712, 6), ModelStatus.Downloaded, true, true),
                    ModelState(
                        info("qwen", "Qwen3.5 0.8B", 964_566_992, 4),
                        ModelStatus.Downloading(400_000_000, 964_566_992),
                        false,
                        true,
                    ),
                    ModelState(info("big", "Large model", 5_000_000_000, 16), ModelStatus.NotDownloaded, false, false),
                ),
                embeddingModels = listOf(
                    ModelState(
                        info("embed", "EmbeddingGemma", 164_626_432, 2, ModelKind.EMBEDDING),
                        ModelStatus.NotDownloaded,
                        false,
                        true,
                    ),
                ),
                deviceRamGb = 8,
                recommendedId = "gemma",
            ),
            actions = actions,
            onAllowMobileData = {},
            onBack = {},
        )
    }
}
