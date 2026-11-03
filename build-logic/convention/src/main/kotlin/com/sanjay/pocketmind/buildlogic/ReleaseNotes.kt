package com.sanjay.pocketmind.buildlogic

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/**
 * Play Store "What's new" text from conventional commit subjects (`feat:`, `fix:`, `perf:`).
 * Commits for developers only (`chore`, `docs`, `test`, `ci`, `build`, `refactor`, `style`) are left
 * out: users can't see them.
 */
object ReleaseNotes {
    /** Google Play's limit for release notes, per language. */
    const val PLAY_LIMIT = 500

    private val pattern = Regex("""^(\w+)(\([^)]*\))?(!)?:\s*(.+)$""")
    private val labels = mapOf("feat" to "New", "fix" to "Fixed", "perf" to "Faster")

    fun fromCommits(subjects: List<String>, limit: Int = PLAY_LIMIT): String {
        val lines = subjects.mapNotNull { subject ->
            val match = pattern.matchEntire(subject.trim()) ?: return@mapNotNull null
            val label = labels[match.groupValues[1].lowercase()] ?: return@mapNotNull null
            "• $label: ${match.groupValues[4].trim().replaceFirstChar { it.uppercase() }}"
        }.distinct()
        if (lines.isEmpty()) return "Small improvements and fixes."
        return fit(lines, limit)
    }

    /** Whole lines only; if they don't all fit, the last line says there is more. */
    private fun fit(lines: List<String>, limit: Int): String {
        val more = "• …and more improvements."
        val kept = mutableListOf<String>()
        for ((index, line) in lines.withIndex()) {
            val isLast = index == lines.lastIndex
            val candidate = (kept + line + if (isLast) emptyList() else listOf(more)).joinToString("\n")
            if (candidate.length > limit) {
                kept += more
                return kept.joinToString("\n").take(limit)
            }
            kept += line
        }
        return kept.joinToString("\n")
    }
}

/**
 * Commit subjects since the PREVIOUS `v*` tag (all commits when there is none); empty when git can't
 * run. Searching from `HEAD^` matters on a tag push: HEAD itself carries the tag being released, and
 * "since that tag" would be nothing. A ValueSource for the configuration cache, like [GitTagValueSource].
 */
abstract class GitCommitSubjectsValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    @Suppress("TooGenericExceptionCaught") // no git means no notes, not a failed build
    override fun obtain(): String = try {
        val tag = run("git", "describe", "--tags", "--match", "v*", "--abbrev=0", "HEAD^")
        val range = if (tag.isNullOrBlank()) "HEAD" else "$tag..HEAD"
        run("git", "log", range, "--no-merges", "--pretty=%s").orEmpty()
    } catch (e: Exception) {
        ""
    }

    private fun run(vararg command: String): String? {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*command)
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) output.toString().trim() else null
    }
}
