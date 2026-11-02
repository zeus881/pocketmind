package com.sanjay.pocketmind.core.common.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wall-clock time in milliseconds, behind an interface.
 *
 * WHY: code that calls `System.currentTimeMillis()` directly can't be tested for things like
 * "messages are ordered by time" — every run gives different values. Tests inject a fake clock.
 */
fun interface Clock {
    fun nowMillis(): Long
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun providesClock(): Clock = Clock { System.currentTimeMillis() }
}
