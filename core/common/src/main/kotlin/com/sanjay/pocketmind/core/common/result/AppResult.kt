package com.sanjay.pocketmind.core.common.result

import kotlinx.coroutines.CancellationException

/**
 * The outcome of an operation that can fail: either [Success] with a value or [Failure]
 * with an [ErrorKind] the UI can turn into a readable message.
 *
 * WHY not just throw exceptions? A sealed result forces the caller to handle the failure
 * case (the `when` must be exhaustive), and it carries a stable error KIND instead of a raw
 * exception message, so the UI can show a translated, user-friendly string.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val kind: ErrorKind, val cause: Throwable? = null) : AppResult<Nothing>
}

/** Every failure category the UI knows how to explain to the user. */
enum class ErrorKind {
    /** No downloaded model is selected yet. */
    NO_MODEL,

    /** The model file exists but the runtime could not load it on any backend. */
    MODEL_LOAD_FAILED,

    /** The model ran but failed while generating. */
    GENERATION_FAILED,

    /** Phone storage is too small for the download. */
    NOT_ENOUGH_STORAGE,

    /** Network error or HTTP failure while downloading. */
    NETWORK,

    /** The downloaded file's SHA-256 did not match the catalog. */
    CHECKSUM_MISMATCH,

    /** A PDF has no extractable text (e.g. a scanned document). */
    NO_TEXT_IN_DOCUMENT,

    /** A file could not be opened or parsed. */
    FILE_UNREADABLE,

    /** A photo was attached but the selected model can't read images. */
    IMAGES_NOT_SUPPORTED,

    /** Anything we did not anticipate. */
    UNKNOWN,
}

/**
 * Runs [block] and wraps the result. Cancellation is re-thrown on purpose: swallowing a
 * [CancellationException] would break structured concurrency (the coroutine would keep
 * running after its scope was cancelled).
 */
@Suppress("TooGenericExceptionCaught") // Converts any failure into a typed result by design.
inline fun <T> runCatchingApp(kind: ErrorKind, block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(kind, e)
}
