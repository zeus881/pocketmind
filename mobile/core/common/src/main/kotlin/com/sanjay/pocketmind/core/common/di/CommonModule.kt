package com.sanjay.pocketmind.core.common.di

import com.sanjay.pocketmind.core.common.diagnostics.AndroidDeviceFacts
import com.sanjay.pocketmind.core.common.diagnostics.CrashLog
import com.sanjay.pocketmind.core.common.diagnostics.CrashReports
import com.sanjay.pocketmind.core.common.diagnostics.DeviceFactsSource
import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.core.common.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin definitions for core:common (V3; replaces v2's Hilt modules one to one).
 * `single` = one instance for the app's lifetime (Hilt `@Singleton`); `factory` = a new one per use.
 */
val commonModule = module {
    single<CoroutineDispatcher>(PmQualifiers.Io) { Dispatchers.IO }
    single<CoroutineDispatcher>(PmQualifiers.Cpu) { Dispatchers.Default }
    single<CoroutineScope>(PmQualifiers.AppScope) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(PmQualifiers.Cpu))
    }
    factory<Clock> { Clock.SYSTEM }
    single { CrashLog(androidContext(), get()) } bind CrashReports::class
    factory<DeviceFactsSource> { AndroidDeviceFacts(androidContext()) }
}
