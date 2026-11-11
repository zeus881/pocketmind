package com.sanjay.pocketmind.feature.benchmark.di

import com.sanjay.pocketmind.feature.benchmark.BenchmarkViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The benchmark screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val benchmarkFeatureModule = module {
    viewModelOf(::BenchmarkViewModel)
}
