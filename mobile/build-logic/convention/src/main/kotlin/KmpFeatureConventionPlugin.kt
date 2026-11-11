import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A `feature:*` module whose screens and ViewModels are shared by Android and iOS (V3 phase 2c): the
 * multiplatform twin of [AndroidFeatureConventionPlugin].
 *
 * - Targets and Compose come from `pocketmind.kmp.android.library` + `pocketmind.kmp.compose`.
 * - ViewModels, `collectAsStateWithLifecycle` and type-safe navigation use JetBrains' multiplatform builds of
 *   AndroidX lifecycle/navigation. On Android they resolve to the same androidx classes the app's NavHost uses,
 *   so a shared screen plugs into the existing Android navigation graph unchanged.
 * - `koinViewModel()` comes from koin-compose-viewmodel, the multiplatform artifact that koin-androidx-compose
 *   itself builds on.
 * - Screenshot tests stay Roborazzi + Robolectric, in androidHostTest. The goldens stay in `src/test/screenshots`
 *   so the images recorded before the move still match; Roborazzi 1.77.0 supports this Android target type
 *   (it references `KotlinMultiplatformAndroidLibraryTarget`).
 *
 * As before, a feature module never depends on another feature module.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("pocketmind.kmp.android.library")
            pluginManager.apply("pocketmind.kmp.compose")
            // Type-safe navigation routes are @Serializable Kotlin objects.
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("io.github.takahirom.roborazzi")
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(":shared:core"))
                    implementation(project(":shared:designsystem"))
                    implementation(libs.lib("koin-core"))
                    implementation(libs.lib("koin-compose-viewmodel"))
                    // Logging in shared ViewModels (on Android it goes through Timber: debug builds only).
                    implementation(libs.lib("kermit"))
                    implementation(libs.lib("jb-lifecycle-runtime-compose"))
                    implementation(libs.lib("jb-lifecycle-viewmodel-compose"))
                    implementation(libs.lib("jb-navigation-compose"))
                    implementation(libs.lib("kotlinx-serialization-json"))
                }
                sourceSets.getByName("androidHostTest").dependencies {
                    // MainDispatcherRule, captureScreenshots, fakes: the same test kit as the Android modules.
                    implementation(project(":core:testing"))
                }
            }
        }
    }
}
