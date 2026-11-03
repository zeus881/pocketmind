package com.sanjay.pocketmind.core.common.diagnostics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DiagnosticsModule {
    @Binds
    abstract fun bindsDeviceFacts(source: AndroidDeviceFacts): DeviceFactsSource
}
