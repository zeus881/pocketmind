package com.manjit.pocketmind.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Java/Kotlin bytecode level for every module. JDK 17 is what AGP 9 requires. */
internal val PocketMindJavaVersion = JavaVersion.VERSION_17

/**
 * Kotlin compiler settings shared by all modules (Android and pure JVM).
 *
 * We configure the compile TASKS instead of a specific extension because AGP 9 compiles
 * Kotlin itself ("built-in Kotlin"), while pure JVM modules use the Kotlin JVM plugin.
 * Both produce [KotlinJvmCompile] tasks, so this works for both.
 */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Treat Kotlin compiler warnings as errors so problems can't pile up silently.
            allWarningsAsErrors.set(true)
        }
    }
    tasks.withType<Test>().configureEach {
        // Gradle 9 fails a test task that finds no tests. KSP/Hilt generate test sources even
        // in modules that have no tests (e.g. the design system), so we allow "no tests".
        failOnNoDiscoveredTests.set(false)
    }
}

/**
 * Android Lint settings shared by the app and every library.
 * Warnings are errors (CLAUDE.md rule); lint.xml at the repo root lists the few checks we
 * disable and explains why.
 */
internal fun Project.configureLint(lint: Lint) {
    lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = rootProject.file("lint.xml")
    }
}
