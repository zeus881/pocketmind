package com.sanjay.pocketmind.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Package name of the app under test. */
const val TARGET_PACKAGE = "com.sanjay.pocketmind"

private const val TIMEOUT_MS = 5_000L

/**
 * On a fresh install the app starts with onboarding. Skip through it so the measured journey
 * is the same for every run: "Skip" jumps to the last page, then "get started" finishes.
 */
fun MacrobenchmarkScope.passOnboardingIfShown() {
    val skip = device.wait(Until.findObject(By.text("Skip")), TIMEOUT_MS) ?: return
    skip.click()
    device.wait(Until.findObject(By.textContains("get started")), TIMEOUT_MS)?.click()
    device.waitForIdle()
    device.pressBack() // leave the model screen, back to the chat
    device.waitForIdle()
}

/** Visits every bottom-bar tab: exercises Compose code that should be in the Baseline Profile. */
fun MacrobenchmarkScope.visitAllTabs() {
    listOf("History", "Docs", "Translate", "Settings", "Chat").forEach { label ->
        device.wait(Until.findObject(By.text(label)), TIMEOUT_MS)?.click()
        device.waitForIdle()
    }
}
