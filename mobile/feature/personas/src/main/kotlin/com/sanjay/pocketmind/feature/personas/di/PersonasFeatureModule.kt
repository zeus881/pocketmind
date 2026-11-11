package com.sanjay.pocketmind.feature.personas.di

import com.sanjay.pocketmind.feature.personas.PersonasViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The personas screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val personasFeatureModule = module {
    viewModelOf(::PersonasViewModel)
}
