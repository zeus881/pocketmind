import com.manjit.pocketmind.buildlogic.lib
import com.manjit.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Hilt (dependency injection) to a module.
 *
 * Hilt generates code at compile time. We run its annotation processor with KSP
 * ("Kotlin Symbol Processing"), which is about 2x faster than the older KAPT.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                add("implementation", libs.lib("hilt-android"))
                add("ksp", libs.lib("hilt-compiler"))
            }
        }
    }
}
