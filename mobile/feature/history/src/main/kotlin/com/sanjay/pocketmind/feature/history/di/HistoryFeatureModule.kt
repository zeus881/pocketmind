package com.sanjay.pocketmind.feature.history.di

import com.sanjay.pocketmind.feature.history.HistoryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The history screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val historyFeatureModule = module {
    viewModelOf(::HistoryViewModel)
}
