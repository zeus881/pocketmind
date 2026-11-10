import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.sanjay.pocketmind.buildlogic.configureKotlin
import com.sanjay.pocketmind.buildlogic.configureKover
import com.sanjay.pocketmind.buildlogic.configureLint
import com.sanjay.pocketmind.buildlogic.intVersion
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A Kotlin Multiplatform module that needs Android APIs in its Android code (V3), e.g. `shared:data`
 * (Room's Android builder, SQLCipher, Keystore live next to the shared schema).
 *
 * Targets: Android (AGP 9's `com.android.kotlin.multiplatform.library`; its target block is
 * `kotlin { android { … } }`, checked in the AGP 9.5.0 API jar) plus iosArm64 and iosSimulatorArm64 (built
 * only on macOS: the ios-build CI job). The namespace comes from the module path, so `:shared:data` becomes
 * `com.sanjay.pocketmind.shared.data`.
 *
 * Pure-Kotlin modules use [KmpLibraryConventionPlugin] instead (JVM target, no Android plugin).
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = "com.sanjay.pocketmind." + path.removePrefix(":").replace(':', '.')
                    compileSdk = libs.intVersion("compileSdk")
                    minSdk = libs.intVersion("minSdk")
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    configureLint(lint)
                }
                iosArm64()
                iosSimulatorArm64()
                compilerOptions {
                    // A warning fails the build on EVERY target (configureKotlin only reaches the JVM tasks).
                    allWarningsAsErrors.set(true)
                    // `expect object` (e.g. Room's generated database constructor) is still a Beta feature;
                    // without this flag every such declaration is a warning, i.e. an error here.
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.lib("kotlin-test"))
                }
            }
            configureKotlin()
            // Kover calls the Android target's single variant "android" (probed with Kover 0.9.11). Its classes join the
            // merged report, so tests in other modules (e.g. core:database's DAO tests) count for this code too.
            configureKover(koverSource = "android")
        }
    }
}
