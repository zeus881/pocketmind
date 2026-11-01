import com.manjit.pocketmind.buildlogic.lib
import com.manjit.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a `feature:*` module (one screen or one group of screens) needs:
 * Android library + Compose + Hilt + type-safe navigation + the shared design system.
 *
 * Note what is NOT here: no feature module ever depends on another feature module.
 * Screens talk to each other only through navigation callbacks wired up in `:app`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("pocketmind.android.library")
            pluginManager.apply("pocketmind.android.compose")
            pluginManager.apply("pocketmind.hilt")
            // Type-safe navigation routes are @Serializable Kotlin objects.
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:designsystem"))

                add("implementation", libs.lib("androidx-hilt-navigation-compose"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
            }
        }
    }
}
