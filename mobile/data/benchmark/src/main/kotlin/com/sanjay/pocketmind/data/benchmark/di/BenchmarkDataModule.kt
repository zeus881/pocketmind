package com.sanjay.pocketmind.data.benchmark.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.data.benchmark.AndroidDeviceMetrics
import com.sanjay.pocketmind.data.benchmark.BenchmarkRepository
import com.sanjay.pocketmind.data.benchmark.BenchmarkRunner
import com.sanjay.pocketmind.data.benchmark.DeviceMetrics
import com.sanjay.pocketmind.data.benchmark.RoomBenchmarkRepository
import org.koin.dsl.module

/**
 * The benchmark: the runner, report and repository are shared code (shared:ai); only the memory reader is
 * Android code.
 */
val benchmarkDataModule = module {
    factory<DeviceMetrics> { AndroidDeviceMetrics() }
    factory<BenchmarkRepository> { RoomBenchmarkRepository(get()) }
    factory { BenchmarkRunner(get(), get(), get(), get(), get(), get(PmQualifiers.Io)) }
}
