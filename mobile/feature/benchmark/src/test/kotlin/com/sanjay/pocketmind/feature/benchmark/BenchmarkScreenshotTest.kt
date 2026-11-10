package com.sanjay.pocketmind.feature.benchmark

import com.sanjay.pocketmind.core.model.BenchmarkPromptResult
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.core.testing.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the benchmark screen, using the numbers measured on the iQOO Neo 10R. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BenchmarkScreenshotTest {
    private val run = BenchmarkResult(
        id = "r1", createdAt = 1_762_000_000_000, modelId = "gemma", modelName = "Gemma 4 E2B", backend = "GPU",
        loadMs = 5_454,
        prompts = listOf(BenchmarkPromptResult("Short answer", 390, 26.3, 128, 5_300, true)),
        peakMemoryMb = 960, device = "iQOO I2221", androidVersion = "16",
    )

    @Test
    fun results() = captureScreenshots("benchmark_results") {
        BenchmarkContent(
            state = BenchmarkUiState(isLoadingHistory = false, runs = listOf(run)),
            onRun = {},
            onCancel = {},
            onShare = {},
            onDelete = {},
            onBack = {},
        )
    }
}
