package com.sanjay.pocketmind.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing WHILE an answer streams into the chat: the most performance-sensitive moment of
 * the app (Markdown re-parsing, list growth, scrolling, 20 UI updates per second).
 *
 * Budget (docs/performance-budget.md): fewer than 1 % of frames slower than 16 ms.
 * Needs a downloaded, selected model on the phone. Run (one line):
 * ```
 * ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.sanjay.pocketmind.benchmark.ChatStreamingBenchmark
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ChatStreamingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun streamingAnswerFrames() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            passOnboardingIfShown()
        },
    ) {
        sendPromptAndWaitForAnswer("Write a short list of five healthy breakfast ideas with one sentence each.")
    }

    private companion object {
        // Each iteration generates a whole answer, so fewer iterations than the startup test.
        const val ITERATIONS = 3
    }
}
