package com.sanjay.pocketmind.core.llm

import android.content.Context
import java.io.File

/**
 * Protects the app from a "crash loop" caused by native code while loading a model.
 *
 * The problem: `Engine.initialize()` runs C++ code (LiteRT-LM, the GPU driver). If that code
 * crashes (SIGSEGV), the whole process dies at once — no Kotlin `catch` or `finally` runs. The
 * chat screen loads the model when it opens, so the next launch would crash again, forever.
 *
 * The idea is a "black box recorder":
 * ```
 *  before initialize():  write  llm-guard/loading   = "GPU\n/path/model.litertlm"
 *  after  initialize():  delete llm-guard/loading   (success OR a normal exception)
 *
 *  next app start, before loading:
 *    marker still there?  → the process died DURING initialize()
 *    and Android says the last exit was a crash (or can't tell, Android < 11)?
 *        → add "GPU <path>" to llm-guard/blocked
 * ```
 * The engine then skips blocked backends: GPU → CPU fallback. If every backend is blocked it
 * reports [FailureReason.CRASHED_LAST_TIME] instead of trying again, and the user decides.
 *
 * A marker left by a NON-crash exit (the user swiped the app away mid-load, a low-memory kill)
 * blocks nothing on Android 11+, because [ExitReasonSource] tells those apart from crashes.
 *
 * Plain files instead of DataStore: the write must be synchronous and finished BEFORE the
 * native call — an async write could be lost in the very crash we want to record.
 */
class LoadGuard internal constructor(private val dir: File, private val exitReasons: ExitReasonSource) {

    constructor(context: Context, exitReasons: ExitReasonSource) :
        this(File(context.filesDir, DIR_NAME), exitReasons)

    private val marker get() = File(dir, MARKER_NAME)
    private val blockedFile get() = File(dir, BLOCKED_NAME)
    private var checkedPreviousRun = false

    /**
     * Turns a marker left behind by a crashed process into a block. Runs once per process;
     * call it before every load (cheap after the first time).
     */
    @Synchronized
    fun recoverFromPreviousRun() {
        if (checkedPreviousRun) return
        checkedPreviousRun = true
        val pending = readMarker()
        marker.delete() // even an unreadable marker must not linger
        if (pending == null) return
        // null (unknown, Android < 11) counts as a crash: wrongly falling back to CPU once is
        // cheap; crashing on every start is not.
        if (exitReasons.previousProcessCrashed() != false) {
            writeBlocks(readBlocks() + pending)
        }
    }

    /** Records "loading [modelPath] on [backend] now". Written synchronously, before native code runs. */
    @Synchronized
    fun begin(modelPath: String, backend: LlmBackend) {
        dir.mkdirs()
        marker.writeText("${backend.name}\n$modelPath")
    }

    /** The load returned (successfully or with a normal exception): nothing to remember. */
    @Synchronized
    fun end() {
        marker.delete()
    }

    /** Backends that crashed the app while loading [modelPath] and must not be tried again automatically. */
    @Synchronized
    fun blockedBackends(modelPath: String): Set<LlmBackend> =
        readBlocks().filter { it.modelPath == modelPath }.map { it.backend }.toSet()

    /** Forget the crashes of [modelPath] (it was re-downloaded or verified). */
    @Synchronized
    fun clear(modelPath: String) {
        writeBlocks(readBlocks().filterNot { it.modelPath == modelPath }.toSet())
    }

    /** Forget every recorded crash (the user pressed "Try again"). */
    @Synchronized
    fun clearAll() {
        blockedFile.delete()
    }

    private data class Entry(val backend: LlmBackend, val modelPath: String)

    private fun readMarker(): Entry? {
        if (!marker.isFile) return null
        val lines = marker.readText().lines()
        val backend = LlmBackend.entries.firstOrNull { it.name == lines.firstOrNull() }
        val path = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
        return if (backend != null && path != null) Entry(backend, path) else null
    }

    private fun readBlocks(): Set<Entry> {
        if (!blockedFile.isFile) return emptySet()
        return blockedFile.readLines().mapNotNull { line ->
            val backend = LlmBackend.entries.firstOrNull { it.name == line.substringBefore(SEPARATOR) }
            val path = line.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (backend == null || path.isEmpty()) null else Entry(backend, path)
        }.toSet()
    }

    private fun writeBlocks(entries: Set<Entry>) {
        if (entries.isEmpty()) {
            blockedFile.delete()
            return
        }
        dir.mkdirs()
        blockedFile.writeText(entries.joinToString("\n") { "${it.backend.name}$SEPARATOR${it.modelPath}" })
    }

    companion object {
        private const val DIR_NAME = "llm-guard"
        private const val MARKER_NAME = "loading"
        private const val BLOCKED_NAME = "blocked"
        private const val SEPARATOR = '\t'

        /** For tests: a guard that keeps its files in any folder. */
        fun forTesting(dir: File, exitReasons: ExitReasonSource) = LoadGuard(dir, exitReasons)
    }
}
