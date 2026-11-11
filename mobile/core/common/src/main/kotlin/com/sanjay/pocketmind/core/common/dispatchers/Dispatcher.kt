package com.sanjay.pocketmind.core.common.dispatchers

import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

/**
 * The dispatchers PocketMind uses, and their Koin names.
 *
 * WHY inject dispatchers instead of calling `Dispatchers.IO` directly?
 * In tests we replace them with a test dispatcher that runs instantly and deterministically.
 * Hard-coded `Dispatchers.IO` would make tests slow and flaky.
 *
 * V3 (Koin): a class takes a plain `CoroutineDispatcher` parameter; its Koin definition passes
 * `get(PmQualifiers.Io)`. Usage: `factory { ModelDownloader(get(), get(), get(PmQualifiers.Io)) }`.
 */
enum class PmDispatchers {
    /** Blocking I/O: files, network, database, and the blocking LiteRT-LM native calls. */
    IO,

    /** CPU-heavy pure-Kotlin work: chunking text, cosine similarity over many vectors. */
    Default,
}

/** Koin names for the two dispatchers and the app-wide coroutine scope. */
object PmQualifiers {
    val Io: Qualifier = named(PmDispatchers.IO)
    val Cpu: Qualifier = named(PmDispatchers.Default)

    /**
     * A CoroutineScope that lives as long as the app process. Some work must finish even if the screen that
     * started it is closed (saving a half-generated answer, unloading the model when Android is low on memory);
     * `viewModelScope` would cancel it too early. Its SupervisorJob keeps one failed child from cancelling others.
     */
    val AppScope: Qualifier = named("ApplicationScope")
}
