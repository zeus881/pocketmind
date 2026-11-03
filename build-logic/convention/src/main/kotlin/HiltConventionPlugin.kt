import com.sanjay.pocketmind.buildlogic.lib
import com.sanjay.pocketmind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
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
            // An intermittent KSP run that produced NO files was stored in the build cache and then
            // restored in later builds as "FROM-CACHE", giving MissingBinding / "could not be resolved"
            // errors far from the cause (PROGRESS.md, BLOCKERS). KSP takes seconds: always run it.
            // Never two KSP tasks at the same time (v2 phase 22). KSP runs as Gradle worker actions, which
            // overlap even in serial builds, and overlapping KSP runs saw incomplete classpaths ("X could not be
            // resolved"). A build service with ONE allowed user serializes exactly the KSP tasks.
            val oneKspAtATime = gradle.sharedServices.registerIfAbsent("oneKspAtATime", KspLock::class.java) {
                maxParallelUsages.set(1)
            }
            tasks.matching { it.name.startsWith("ksp") }.configureEach {
                outputs.doNotCacheIf("KSP output is always regenerated (an empty result was once cached)") { true }
                usesService(oneKspAtATime)
            }
        }
    }
}

/** A token: holding it means "this KSP task may run now". */
abstract class KspLock : BuildService<BuildServiceParameters.None>
