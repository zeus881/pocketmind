package com.sanjay.pocketmind.core.common.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Provides the shared [Clock] (V3: the interface lives in shared:core) to Android's Hilt graph. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun providesClock(): Clock = Clock.SYSTEM
}
