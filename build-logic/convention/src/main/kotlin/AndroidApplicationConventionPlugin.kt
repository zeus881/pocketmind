import com.android.build.api.dsl.ApplicationExtension
import com.sanjay.pocketmind.buildlogic.PocketMindJavaVersion
import com.sanjay.pocketmind.buildlogic.configureKotlin
import com.sanjay.pocketmind.buildlogic.configureLint
import com.sanjay.pocketmind.buildlogic.intVersion
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Setup for the single `:app` module: SDK levels, 64-bit only, Java 17, lint.
 * App-specific things (applicationId, signing, R8) stay in app/build.gradle.kts so they are
 * easy to find.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // LiteRT-LM ships native code only for arm64-v8a (phones) and x86_64.
                    // Real LLM phones are 64-bit ARM, so we ship only that: a smaller APK.
                    ndk { abiFilters += listOf("arm64-v8a") }
                }
                compileOptions {
                    sourceCompatibility = PocketMindJavaVersion
                    targetCompatibility = PocketMindJavaVersion
                }
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
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
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            }
        }
    }
}
