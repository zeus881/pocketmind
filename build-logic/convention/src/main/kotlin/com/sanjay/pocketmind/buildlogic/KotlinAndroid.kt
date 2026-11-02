package com.sanjay.pocketmind.buildlogic

import com.android.build.api.dsl.Lint
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
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
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<Test>().configureEach {
        // Gradle 9 fails a test task that finds no tests. KSP/Hilt generate test sources even
        // in modules that have no tests (e.g. the design system), so we allow "no tests".
        failOnNoDiscoveredTests.set(false)
        // Unit tests RUN on JDK 21 (Robolectric's Android 36 image and Java-21 libraries need it),
        // while the code is still COMPILED to Java 17 bytecode for the app.
        javaLauncher.set(
            toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libs.intVersion("testJdk"))) },
        )
        // Robolectric simulates Android by reaching into JDK internals (e.g. FileDescriptor).
        // Since Java 17 the module system blocks that unless the packages are "opened".
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
    }
}

/**
 * Runs JVM unit tests for the DEBUG build type only.
 *
 * WHY: AGP creates a unit-test task per build type (debug, release, and the app's extra
 * benchmark/nonMinified types). They run the same tests against the same Kotlin code, so the
 * extra runs only double the build time — and Kover's merged report would run all of them.
 */
internal fun Project.configureHostTestsForDebugOnly() {
    // Looked up by name: AGP registers it under its concrete type (application or library).
    val components = extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>
    components.beforeVariants { variant ->
        if (variant.buildType != "debug") {
            (variant as? HasHostTestsBuilder)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.enable = false
        }
    }
}

/**
 * Code coverage with Kover. Every module applies the plugin; the root project merges all
 * modules into one report and enforces the minimum (see the root build.gradle.kts).
 *
 * Kover's default "total" report covers EVERY build type, so it compiles the release variants
 * too — slow, and it adds nothing because the unit tests only run on debug. Instead each module
 * contributes one custom variant named [COVERAGE_VARIANT] holding just its tested code:
 * [koverSource] is "debug" for Android modules and "jvm" for pure Kotlin modules.
 * The root then offers `koverVerifyApp`, `koverXmlReportApp` and `koverHtmlReportApp`.
 */
internal fun Project.configureKover(koverSource: String) {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject {
            createVariant(COVERAGE_VARIANT) { add(koverSource) }
        }
    }
}

/** Name of the merged coverage variant (tasks: koverVerifyApp, koverXmlReportApp, …). */
internal const val COVERAGE_VARIANT = "app"

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
