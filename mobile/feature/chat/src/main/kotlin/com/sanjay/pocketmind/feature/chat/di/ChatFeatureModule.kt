package com.sanjay.pocketmind.feature.chat.di

import com.sanjay.pocketmind.feature.chat.ChatViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The chat screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val chatFeatureModule = module {
    viewModelOf(::ChatViewModel)
}
