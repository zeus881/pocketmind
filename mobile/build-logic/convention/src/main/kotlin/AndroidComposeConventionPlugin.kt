import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Turns on Jetpack Compose for a module.
 *
 * Since Kotlin 2.0 the Compose compiler is a Kotlin compiler plugin
 * (`org.jetbrains.kotlin.plugin.compose`) whose version always equals the Kotlin version.
 * The Compose BOM ("bill of materials") then picks matching versions for all Compose libraries.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures { compose = true } }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures { compose = true } }
            }

            dependencies {
                val bom = platform(libs.lib("compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.lib("compose-ui"))
                add("implementation", libs.lib("compose-ui-graphics"))
                add("implementation", libs.lib("compose-foundation"))
                add("implementation", libs.lib("compose-material3"))
                add("implementation", libs.lib("compose-material-icons-extended"))
                add("implementation", libs.lib("compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("compose-ui-tooling"))
                // Compose UI tests run on the JVM with Robolectric (no phone needed).
                add("testImplementation", libs.lib("compose-ui-test-junit4"))
                add("testImplementation", libs.lib("robolectric"))
                add("debugImplementation", libs.lib("compose-ui-test-manifest"))
            }
        }
    }
}
