package com.manjit.pocketmind.core.common.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Emits at most one value every [periodMillis], always ending with the LATEST value.
 *
 * WHY: an LLM can produce 30–60 tokens per second. Redrawing the chat on every token
 * makes Compose recompose 60 times a second and causes jank. With a 50 ms throttle the UI
 * updates at most 20 times a second, which still looks perfectly smooth to a human.
 *
 * HOW it works:
 * - [conflate] runs the upstream in its own coroutine and keeps only the newest value
 *   while the downstream is busy (older values are dropped, never queued).
 * - [transform] emits a value, then sleeps for [periodMillis]. While it sleeps, new values
 *   overwrite each other in the conflated buffer. After the sleep we get the newest one.
 * - When upstream completes, the last value still in the buffer is emitted, so the final
 *   text is never lost.
 */
fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform { value ->
    emit(value)
    delay(periodMillis)
}
