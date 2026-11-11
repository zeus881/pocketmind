package com.sanjay.pocketmind.feature.onboarding.di

import com.sanjay.pocketmind.feature.onboarding.OnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The onboarding screen's ViewModel(s), created by Koin (V3; v2 used @HiltViewModel). */
val onboardingFeatureModule = module {
    viewModelOf(::OnboardingViewModel)
}
