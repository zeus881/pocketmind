import androidx.room.gradle.RoomExtension
import com.sanjay.pocketmind.buildlogic.configureKspSafety
import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Room in a Kotlin Multiplatform module (V3): one schema, entities and DAOs in commonMain for Android and iOS.
 * Apply after `pocketmind.kmp.android.library` (it creates the targets this plugin wires KSP into).
 *
 * - Room's annotation processor runs once per target (`kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`):
 *   each platform gets its own generated DAO code.
 * - Schemas are exported to `schemas/` and committed: they are the migration tests' "old databases".
 * - `sqlite-bundled` is the SQLite driver iOS uses (Android keeps its open helper; see core:database).
 *   2.6.2 = the androidx.sqlite version Room 2.8.5 itself requires (its Gradle module metadata).
 */
class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    // api: DAOs and RoomDatabase are part of this module's public types.
                    api(libs.lib("androidx-room-runtime"))
                    implementation(libs.lib("androidx-sqlite-bundled"))
                }
            }
            dependencies {
                listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { configuration ->
                    add(configuration, libs.lib("androidx-room-compiler"))
                }
            }
            configureKspSafety()
        }
    }
}
