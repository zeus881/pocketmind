package com.sanjay.pocketmind.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [DataStore] that keeps preferences in memory. Tests of code built on DataStore (like
 * `SettingsDataSource`) use it instead of a real file: it is instant, deterministic and works
 * the same on every OS (the file-based DataStore can't replace files on Windows JVMs).
 *
 * The Mutex makes [updateData] atomic, matching the real DataStore's transaction guarantee.
 */
class InMemoryPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}
