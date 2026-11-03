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

// Lets modules write projects.core.model instead of project(":core:model") (typo-safe).
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:llm")
include(":core:testing")
include(":core:database")
include(":core:datastore")
include(":core:speech")
include(":core:tools")

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
