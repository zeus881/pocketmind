package com.sanjay.pocketmind.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold startup (app process killed before every iteration) with and without the
 * Baseline Profile, so the README can show the real difference on a real phone.
 * Goal from the build guide: cold start under 800 ms (the model is NOT loaded at startup).
 *
 * Run on a connected phone: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoProfile() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    /** Frame timing while switching through all tabs (jank check: target < 1% slow frames). */
    @Test
    fun tabSwitchingFrames() = rule.measureRepeated(
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
        visitAllTabs()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
