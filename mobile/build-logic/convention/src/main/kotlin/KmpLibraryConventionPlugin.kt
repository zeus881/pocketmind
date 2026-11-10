import com.sanjay.pocketmind.buildlogic.configureKotlin
import com.sanjay.pocketmind.buildlogic.configureKover
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A pure Kotlin Multiplatform module shared by Android and iOS (V3), e.g. `shared:core`.
 *
 * Code goes in `src/commonMain`; it may not use `java.*` or `android.*` APIs, because the same
 * source is compiled for iOS by Kotlin/Native. Platform code, when a module needs some, goes in
 * `jvmMain` / `iosMain` behind an `expect` declaration.
 *
 * Targets:
 * - **jvm**: the Android modules consume this variant (a plain JVM jar, exactly what v2's
 *   `core:model` was), and the common tests run here, fast, on the PC and on CI.
 * - **iosArm64** (iPhones) and **iosSimulatorArm64** (the simulator on Apple-silicon Macs): Kotlin/Native
 *   can only build Apple targets on a macOS host, so on Windows/Linux Gradle skips them and the
 *   `ios-build` CI job (macOS runner) compiles and tests them.
 *
 * WHY no Android target: a module without Android APIs doesn't need one, and the JVM jar keeps the
 * existing Android modules working unchanged. Modules that DO use Android APIs (phase 2: data, ai)
 * add `com.android.kotlin.multiplatform.library` instead.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                iosArm64()
                iosSimulatorArm64()
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.lib("kotlin-test"))
                }
            }
            // Same compiler rules as every other module (JVM 17 bytecode, warnings are errors, tests on JDK 21).
            configureKotlin()
            // Coverage of the shared logic comes from the JVM target's tests.
            configureKover(koverSource = "jvm")
        }
    }
}
