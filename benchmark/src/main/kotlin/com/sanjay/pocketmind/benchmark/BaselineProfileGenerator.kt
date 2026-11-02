package com.sanjay.pocketmind.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile: the list of classes/methods used at startup and on the main
 * screens. It is shipped inside the APK; when the app is installed, Android compiles exactly
 * those methods to machine code ahead of time (AOT), so the first launch doesn't run them in the
 * slower interpreter/JIT. Typical gain: 20–40% faster cold start and smoother first scrolls.
 *
 * Run on a connected phone: `./gradlew :app:generateBaselineProfile`
 * The result is written to app/src/release/generated/baselineProfiles/ and should be committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        passOnboardingIfShown()
        visitAllTabs()
    }
}
