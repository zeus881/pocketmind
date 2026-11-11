package com.sanjay.pocketmind.feature.documents.di

import com.sanjay.pocketmind.feature.documents.AskViewModel
import com.sanjay.pocketmind.feature.documents.SummarizeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The documents screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val documentsFeatureModule = module {
    viewModelOf(::AskViewModel)
    viewModelOf(::SummarizeViewModel)
}
