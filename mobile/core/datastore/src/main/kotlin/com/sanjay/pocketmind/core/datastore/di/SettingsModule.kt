package com.sanjay.pocketmind.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the shared [SettingsDataSource] (V3: it lives in shared:data without DI annotations, so iOS can
 * create it too). Separate from [DataStoreModule] on purpose: end-to-end tests replace only the FILE
 * (an in-memory DataStore) and still get this real settings class on top of it.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun providesSettingsDataSource(dataStore: DataStore<Preferences>): SettingsDataSource =
        SettingsDataSource(dataStore)
}
