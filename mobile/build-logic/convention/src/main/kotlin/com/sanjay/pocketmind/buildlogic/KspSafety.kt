package com.sanjay.pocketmind.buildlogic

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Protections every module that runs KSP needs (Hilt in Android modules, Room in shared KMP modules).
 *
 * - An intermittent KSP run that produced NO files was stored in the build cache and then restored in
 *   later builds as "FROM-CACHE", giving MissingBinding / "could not be resolved" errors far from the
 *   cause (PROGRESS.md, BLOCKERS). KSP takes seconds: always run it.
 * - Never two KSP tasks at the same time (v2 phase 22). KSP runs as Gradle worker actions, which overlap
 *   even in serial builds, and overlapping KSP runs saw incomplete classpaths ("X could not be resolved").
 *   A build service with ONE allowed user serializes exactly the KSP tasks.
 */
internal fun Project.configureKspSafety() {
    val oneKspAtATime = gradle.sharedServices.registerIfAbsent("oneKspAtATime", KspLock::class.java) {
        maxParallelUsages.set(1)
    }
    tasks.matching { it.name.startsWith("ksp") }.configureEach {
        outputs.doNotCacheIf("KSP output is always regenerated (an empty result was once cached)") { true }
        usesService(oneKspAtATime)
    }
}

/** A token: holding it means "this KSP task may run now". */
abstract class KspLock : BuildService<BuildServiceParameters.None>
