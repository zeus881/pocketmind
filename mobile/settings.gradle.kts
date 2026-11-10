// settings.gradle.kts runs first in every build. It tells Gradle WHERE to find plugins and
// libraries, and WHICH modules exist in this project.
pluginManagement {
    // build-logic is a separate mini-project that contains our own convention plugins.
    // "includeBuild" compiles it first so modules can apply e.g. id("pocketmind.android.library").
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules may not declare their own repositories: one central list is easier to audit.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "PocketMind"

// Lets modules write projects.shared.core instead of project(":shared:core") (typo-safe).
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":androidApp")

// V3 shared Kotlin Multiplatform code (Android + iOS). composeApp = the shared UI, built as the iOS framework.
include(":composeApp")
include(":shared:core")
include(":shared:data")
include(":shared:tools")
include(":shared:ai")
include(":shared:designsystem")

include(":core:common")

include(":core:llm")
include(":core:testing")
include(":core:database")
include(":core:datastore")
include(":core:speech")
include(":core:billing")

include(":data:chat")
include(":data:models")
include(":data:benchmark")
include(":data:documents")

include(":feature:chat")
include(":feature:history")
include(":feature:models")
include(":feature:settings")
include(":feature:benchmark")
include(":feature:onboarding")
include(":feature:documents")
include(":feature:translate")
include(":feature:personas")

include(":benchmark")
