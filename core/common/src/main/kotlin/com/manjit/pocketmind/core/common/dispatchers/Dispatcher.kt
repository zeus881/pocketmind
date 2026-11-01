package com.manjit.pocketmind.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Hilt "qualifier" that says WHICH CoroutineDispatcher to inject.
 *
 * WHY inject dispatchers instead of calling `Dispatchers.IO` directly?
 * In tests we replace them with a test dispatcher that runs instantly and deterministically.
 * Hard-coded `Dispatchers.IO` would make tests slow and flaky.
 *
 * Usage: `class Foo @Inject constructor(@Dispatcher(PmDispatchers.IO) val io: CoroutineDispatcher)`
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: PmDispatchers)

/** The dispatchers PocketMind uses. */
enum class PmDispatchers {
    /** Blocking I/O: files, network, database, and the blocking LiteRT-LM native calls. */
    IO,

    /** CPU-heavy pure-Kotlin work: chunking text, cosine similarity over many vectors. */
    Default,
}
