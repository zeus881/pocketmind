package com.manjit.pocketmind.data.benchmark.di

import com.manjit.pocketmind.data.benchmark.AndroidDeviceMetrics
import com.manjit.pocketmind.data.benchmark.BenchmarkRepository
import com.manjit.pocketmind.data.benchmark.DeviceMetrics
import com.manjit.pocketmind.data.benchmark.RoomBenchmarkRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BenchmarkDataModule {
    @Binds
    abstract fun bindsBenchmarkRepository(repository: RoomBenchmarkRepository): BenchmarkRepository

    @Binds
    abstract fun bindsDeviceMetrics(metrics: AndroidDeviceMetrics): DeviceMetrics
}
