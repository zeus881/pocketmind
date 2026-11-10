package com.sanjay.pocketmind.buildlogic

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/**
 * The newest `v*` git tag reachable from HEAD, or null when there is none OR git can't run.
 *
 * WHY a ValueSource: Gradle's configuration cache must know about every external process the
 * build script starts. `providers.exec { }` does that, but if the `git` executable is missing it
 * fails while the cache is being stored, outside any try/catch in the build script, so the whole
 * build failed on machines without git on the PATH. A ValueSource runs the command inside
 * [obtain], where the failure can be caught and turned into "no tag".
 */
abstract class GitTagValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    @Suppress("TooGenericExceptionCaught") // any failure to run git simply means "no tag"
    override fun obtain(): String? = try {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--abbrev=0")
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (result.exitValue == 0) output.toString().trim().ifBlank { null } else null
    } catch (e: Exception) {
        null
    }
}
