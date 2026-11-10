import io.gitlab.arturbosch.detekt.Detekt

// Root build file. It only declares plugin VERSIONS ("apply false") so that every module
// uses the same version, and it configures the repo-wide quality tools (Spotless, Detekt).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// ---------- Kover: one merged coverage report for the whole app ----------
// Every module applies Kover through its convention plugin; here the root collects them all.
// core:testing (test helpers) and :benchmark (device-only tests) are not app code.

/** Minimum line coverage in percent. Raised phase by phase towards 80 (V2_PLAN.md). Never lowered. */
val koverMinLineCoverage = 84 // v2 phase 22: 84.2 % measured (target 80 reached in phase 7; never lower)

val coveredModules = listOf(
    ":androidApp",
    ":shared:core", ":shared:data", ":core:common", ":core:designsystem", ":core:llm",
    ":core:database", ":core:datastore", ":core:speech", ":core:tools", ":core:billing",
    ":data:chat", ":data:models", ":data:benchmark", ":data:documents",
    ":feature:chat", ":feature:history", ":feature:models", ":feature:settings",
    ":feature:benchmark", ":feature:onboarding", ":feature:documents", ":feature:translate", ":feature:personas",
)

dependencies {
    coveredModules.forEach { kover(project(it)) }
}

kover {
    // The merged variant: each module adds its own "app" variant (debug build or JVM code).
    currentProject {
        createVariant("app") { }
    }
    reports {
        filters {
            excludes {
                // Generated code (Hilt, Room, Compose compiler, serialization) is not ours to test.
                classes(
                    "*Hilt_*", "*_HiltModules*", "*_Factory*", "*_MembersInjector", "*_Provide*Factory*",
                    "dagger.*", "hilt_aggregated_deps.*", "*_GeneratedInjector",
                    "*_Impl", "*_Impl$*", "*_AutoMigration_*",
                    "*ComposableSingletons*", "*.BuildConfig", "*\$\$serializer",
                    // System entry points bound by the launcher / System UI, verified on a device
                    // (their Intents are unit-tested in WidgetIntentsTest).
                    "*.widget.PocketMindWidget", "*.widget.PocketMindWidget\$*", "*.widget.PocketMindWidgetReceiver",
                    "*.widget.NewChatTileService",
                )
                // Wiring only: DI modules and navigation graph builders. Design tokens (colors,
                // type scale) are constants without logic, checked by screenshot tests instead.
                packages("*.di", "*.navigation", "*.designsystem.theme")
                // Composable UI is verified by screenshot and UI tests, not by line coverage
                // (CLAUDE.md §0 "v2 quality gates").
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
            }
        }
        verify {
            rule("Line coverage of app logic") {
                // Raised phase by phase towards 80 % (V2_PLAN.md). Never lowered.
                minBound(koverMinLineCoverage)
            }
        }
    }
}

// ---------- Spotless: one formatting style for every Kotlin file ----------
spotless {
    // Explicit LF endings (matches .editorconfig). The default "read .gitattributes" policy
    // cannot be cached by Gradle 9 and fails the build.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    // Only source folders: scanning build/ folders races with tasks writing into them.
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts", "build-logic/**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

// ---------- Detekt: static analysis for code smells ----------
// Detekt runs once from the root over all modules' sources. Running it at the root (instead
// of applying it inside every Android module) keeps it independent from the Android Gradle
// Plugin's internals, which change between AGP major versions.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/**/kotlin/**/*.kt")
            exclude("**/build/**", "build-logic/**")
        },
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

// Detekt 1.23.x embeds the Kotlin 2.0.21 compiler for parsing. Keep Gradle from "upgrading"
// it to the project's Kotlin version, which would break Detekt at runtime.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}

// ---------- Build-logic tests ----------
// build-logic is an included build, so its unit tests (e.g. AppVersionTest) don't run with the
// app's. This root task has the same name as the modules' test tasks, so the usual
// `./gradlew testDebugUnitTest` runs them too.
tasks.register("testDebugUnitTest") {
    description = "Runs the build-logic unit tests together with the modules' debug unit tests."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
    // Pure Kotlin modules have a plain "test" task; KMP modules test their JVM target with "jvmTest".
    dependsOn(":shared:core:jvmTest", ":core:tools:test")
}

// ---------- Design-token guard ----------
// Screens must use the design system's tokens (PmSpacing, PmSize, MaterialTheme colours), never
// raw "16.dp" or "Color(0xFF…)": that is what keeps every screen on the same grid and palette.
// Runs with `./gradlew lint` (a root task named lint depends on it). Design tokens themselves
// live in core:designsystem, which is not scanned.
val checkDesignTokens = tasks.register("checkDesignTokens") {
    description = "Fails if app or feature screens use raw dp values or colours instead of design tokens."
    val sources = fileTree(rootDir) {
        include("androidApp/src/main/**/*.kt", "feature/*/src/main/**/*.kt", "composeApp/src/*/kotlin/**/*.kt")
        // Token files are the one place raw values belong.
        exclude("**/*Tokens.kt")
    }
    val root = rootDir
    inputs.files(sources)
    doLast {
        val rawDp = Regex("""(?<![\w.])\d+(\.\d+)?\.dp\b""")
        val rawColor = Regex("""Color\(0x""")
        val problems = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val raw = rawDp.containsMatchIn(line) || rawColor.containsMatchIn(line)
                if (raw) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }
        check(problems.isEmpty()) { "Use design tokens instead of raw values:\n" + problems.joinToString("\n") }
    }
}

tasks.register("lint") {
    description = "Root part of `./gradlew lint`: the design-token guard."
    dependsOn(checkDesignTokens)
}

// ---------- Architecture guard ----------
// Fails the build if a module breaks the dependency rules from CLAUDE.md:
//   feature -> data, core, shared   (never feature -> feature)
//   data    -> core, shared
//   core    -> core, shared
//   shared  -> shared only          (V3 Kotlin Multiplatform layer: Android + iOS, no app code)
// KMP modules declare dependencies per source set (commonMainImplementation, …), so those count too.
gradle.projectsEvaluated {
    val violations = mutableListOf<String>()
    rootProject.subprojects.forEach { module ->
        val from = module.path
        val targets = module.configurations
            .filter { config ->
                config.name in setOf("api", "implementation") ||
                    config.name.endsWith("MainApi") || config.name.endsWith("MainImplementation")
            }
            .flatMap { it.dependencies.withType<ProjectDependency>() }
            .map { it.path }
        targets.forEach { to ->
            val broken = when {
                from.startsWith(":feature:") -> to.startsWith(":feature:")
                from.startsWith(":data:") -> !to.startsWith(":core:") && !to.startsWith(":shared:")
                from.startsWith(":core:") -> !to.startsWith(":core:") && !to.startsWith(":shared:")
                from.startsWith(":shared:") -> !to.startsWith(":shared:")
                else -> false
            }
            if (broken) violations += "$from must not depend on $to"
        }
    }
    check(violations.isEmpty()) { "Module dependency rule violations:\n" + violations.joinToString("\n") }
}
