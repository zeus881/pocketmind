package com.sanjay.pocketmind.di

import com.sanjay.pocketmind.MainActivityViewModel
import com.sanjay.pocketmind.ModelKeepAlive
import com.sanjay.pocketmind.core.billing.di.billingModule
import com.sanjay.pocketmind.core.common.di.commonModule
import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.core.database.di.databaseFileModule
import com.sanjay.pocketmind.core.database.di.databaseModule
import com.sanjay.pocketmind.core.datastore.di.dataStoreModule
import com.sanjay.pocketmind.core.datastore.di.settingsModule
import com.sanjay.pocketmind.core.llm.di.llmModule
import com.sanjay.pocketmind.core.llm.di.llmSupportModule
import com.sanjay.pocketmind.core.speech.di.speechModule
import com.sanjay.pocketmind.data.benchmark.di.benchmarkDataModule
import com.sanjay.pocketmind.data.chat.di.chatDataModule
import com.sanjay.pocketmind.data.documents.di.documentsDataModule
import com.sanjay.pocketmind.data.models.di.modelSelectionModule
import com.sanjay.pocketmind.data.models.di.modelsDataModule
import com.sanjay.pocketmind.feature.benchmark.di.benchmarkFeatureModule
import com.sanjay.pocketmind.feature.chat.di.chatFeatureModule
import com.sanjay.pocketmind.feature.documents.di.documentsFeatureModule
import com.sanjay.pocketmind.feature.history.di.historyFeatureModule
import com.sanjay.pocketmind.feature.models.di.modelsFeatureModule
import com.sanjay.pocketmind.feature.onboarding.di.onboardingFeatureModule
import com.sanjay.pocketmind.feature.personas.di.personasFeatureModule
import com.sanjay.pocketmind.feature.settings.di.settingsFeatureModule
import com.sanjay.pocketmind.feature.translate.di.translateFeatureModule
import com.sanjay.pocketmind.play.InAppReview
import com.sanjay.pocketmind.play.InAppUpdates
import com.sanjay.pocketmind.play.playModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The app shell's own objects: the start-up ViewModel, the model keep-alive and the Play helpers. */
val appModule = module {
    viewModelOf(::MainActivityViewModel)
    single { ModelKeepAlive(get(), get(), get(PmQualifiers.AppScope)) }
    single { InAppReview(get(), get(), get()) }
    single { InAppUpdates(get()) }
}

/**
 * Every Koin module of the Android app, in one list (V3: Koin replaces Hilt). The "file" modules
 * ([databaseFileModule], [dataStoreModule]) and the outside-world modules ([llmModule], [billingModule],
 * [playModule], [modelSelectionModule]) are the ones end-to-end tests swap for fakes.
 */
val pocketMindModules: List<Module> = listOf(
    commonModule,
    databaseModule,
    databaseFileModule,
    dataStoreModule,
    settingsModule,
    llmModule,
    llmSupportModule,
    speechModule,
    billingModule,
    chatDataModule,
    documentsDataModule,
    benchmarkDataModule,
    modelsDataModule,
    modelSelectionModule,
    playModule,
    appModule,
    benchmarkFeatureModule,
    chatFeatureModule,
    documentsFeatureModule,
    historyFeatureModule,
    modelsFeatureModule,
    onboardingFeatureModule,
    personasFeatureModule,
    settingsFeatureModule,
    translateFeatureModule,
)
