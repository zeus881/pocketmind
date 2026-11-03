package com.sanjay.pocketmind.data.chat

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "An answer was completed" (not stopped, not failed), for parts of the app that react to use, such
 * as the Play review prompt. A hot stream: whoever listens at that moment hears it; nothing is stored.
 */
@Singleton
class AnswerEvents @Inject constructor() {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed: SharedFlow<Unit> = events.asSharedFlow()

    internal fun answerCompleted() {
        events.tryEmit(Unit)
    }
}
