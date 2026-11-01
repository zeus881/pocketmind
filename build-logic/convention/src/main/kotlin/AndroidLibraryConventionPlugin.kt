import com.android.build.api.dsl.LibraryExtension
import com.manjit.pocketmind.buildlogic.PocketMindJavaVersion
import com.manjit.pocketmind.buildlogic.configureKotlin
import com.manjit.pocketmind.buildlogic.configureLint
import com.manjit.pocketmind.buildlogic.intVersion
import com.manjit.pocketmind.buildlogic.lib
import com.manjit.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Base setup for every Android library module (core:*, data:*, feature:*).
 *
 * WHY: without this, each of ~20 modules would repeat the same 40 lines of Gradle config.
 * A convention plugin keeps them identical and lets us change e.g. minSdk in one place.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // Keep rules a library needs when the APP runs R8 (see consumer-rules.pro).
                    if (file("consumer-rules.pro").exists()) consumerProguardFiles("consumer-rules.pro")
                }
                compileOptions {
                    sourceCompatibility = PocketMindJavaVersion
                    targetCompatibility = PocketMindJavaVersion
                }
                testOptions {
                    // Robolectric simulates this Android version. Without it, libraries would use
                    // compileSdk (37). Only affects JVM unit tests, never the shipped app.
                    targetSdk = libs.intVersion("targetSdk")
                    unitTests {
                        // Robolectric tests need merged resources (strings, themes).
                        isIncludeAndroidResources = true
                        // android.util.Log etc. return defaults in plain JVM tests instead of crashing.
                        isReturnDefaultValues = true
                    }
                }
                configureLint(lint)
            }
            configureKotlin()

            dependencies {
                add("implementation", libs.lib("timber"))
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("mockk"))
                add("testImplementation", libs.lib("turbine"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
