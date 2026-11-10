package com.sanjay.pocketmind.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A CoroutineScope that lives as long as the app process.
 *
 * WHY: some work must finish even if the screen that started it is closed — for example
 * saving a half-generated answer when the user leaves the chat, or unloading the model when
 * Android is low on memory. `viewModelScope` would cancel that work too early.
 *
 * `SupervisorJob` means one failed child coroutine does not cancel the other children.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(@Dispatcher(PmDispatchers.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
