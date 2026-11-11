package com.sanjay.pocketmind.feature.models.di

import com.sanjay.pocketmind.feature.models.ModelsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The models screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val modelsFeatureModule = module {
    viewModelOf(::ModelsViewModel)
}
