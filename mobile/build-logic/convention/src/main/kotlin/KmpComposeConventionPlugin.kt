import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compose Multiplatform UI in a KMP module (V3): `shared:designsystem`, `composeApp`.
 * Apply after `pocketmind.kmp.android.library` (it creates the Android and iOS targets).
 *
 * The JetBrains Compose artifacts resolve, on Android, to the same AndroidX Compose classes the v2 screens use
 * (Compose Multiplatform 1.12.2 is built on AndroidX Compose 1.12.1), so Android-only and shared screens can be
 * mixed while the screens move over (V3 phase 2c).
 *
 * Android host tests get Robolectric + Compose's JUnit 4 test rule, like v2's Android Compose modules.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    // composeResources/ strings are packaged as Android resources.
                    androidResources { enable = true }
                }
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.lib("jb-compose-runtime"))
                    implementation(libs.lib("jb-compose-foundation"))
                    implementation(libs.lib("jb-compose-ui"))
                    implementation(libs.lib("jb-compose-material3"))
                    implementation(libs.lib("jb-compose-material-icons-extended"))
                    implementation(libs.lib("jb-compose-components-resources"))
                }
                sourceSets.getByName("androidHostTest").dependencies {
                    implementation(project.dependencies.platform(libs.lib("compose-bom")))
                    implementation(libs.lib("compose-ui-test-junit4"))
                    implementation(libs.lib("compose-ui-test-manifest"))
                    implementation(libs.lib("robolectric"))
                    implementation(libs.lib("androidx-test-core"))
                }
            }
        }
    }
}
