package com.sanjay.pocketmind.feature.settings.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The settings screen's ViewModel, created by Koin (V3; v2 used @HiltViewModel). */
val settingsFeatureModule = module {
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(PmQualifiers.Io)) }
}
