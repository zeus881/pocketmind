import com.sanjay.pocketmind.buildlogic.PocketMindJavaVersion
import com.sanjay.pocketmind.buildlogic.configureKotlin
import com.sanjay.pocketmind.buildlogic.configureKover
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * A pure Kotlin module with no Android code at all (used by `core:model`).
 *
 * WHY: pure Kotlin modules build faster, and their tests run on the plain JVM in milliseconds.
 * It also proves our data models don't secretly depend on Android.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = PocketMindJavaVersion
                targetCompatibility = PocketMindJavaVersion
            }
            configureKotlin()
            configureKover(koverSource = "jvm")
            dependencies {
                add("testImplementation", libs.lib("junit"))
            }
        }
    }
}
