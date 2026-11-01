import io.gitlab.arturbosch.detekt.Detekt

// Root build file. It only declares plugin VERSIONS ("apply false") so that every module
// uses the same version, and it configures the repo-wide quality tools (Spotless, Detekt).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
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

// ---------- Architecture guard ----------
// Fails the build if a module breaks the dependency rules from CLAUDE.md:
//   feature -> data, core   (never feature -> feature)
//   data    -> core only
//   core    -> core only
gradle.projectsEvaluated {
    val violations = mutableListOf<String>()
    rootProject.subprojects.forEach { module ->
        val from = module.path
        val targets = module.configurations
            .filter { it.name == "api" || it.name == "implementation" }
            .flatMap { it.dependencies.withType<ProjectDependency>() }
            .map { it.path }
        targets.forEach { to ->
            val broken = when {
                from.startsWith(":feature:") -> to.startsWith(":feature:")
                from.startsWith(":data:") -> !to.startsWith(":core:")
                from.startsWith(":core:") -> !to.startsWith(":core:")
                else -> false
            }
            if (broken) violations += "$from must not depend on $to"
        }
    }
    check(violations.isEmpty()) { "Module dependency rule violations:\n" + violations.joinToString("\n") }
}
