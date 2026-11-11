package com.sanjay.pocketmind.core.datastore.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The settings FILE. Exactly ONE DataStore instance per file is allowed in a process (two on the same
 * file would corrupt it), so it is a `single`. The file name is unchanged since v1.
 * Separate from [settingsModule] so end-to-end tests can replace only the file (an in-memory DataStore).
 */
val dataStoreModule = module {
    single { PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("user_settings") } }
}

/** The shared settings logic (V3: lives in shared:data without DI annotations) on top of that file. */
val settingsModule = module {
    single { SettingsDataSource(get()) }
}
