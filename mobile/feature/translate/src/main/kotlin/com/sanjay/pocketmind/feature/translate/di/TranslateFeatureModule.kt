package com.sanjay.pocketmind.feature.translate.di

import com.sanjay.pocketmind.feature.translate.TranslateViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The translate screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val translateFeatureModule = module {
    viewModelOf(::TranslateViewModel)
}
