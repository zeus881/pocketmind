package com.sanjay.pocketmind

import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())

    private suspend fun MainActivityViewModel.ready() = uiState.first { it is MainUiState.Ready } as MainUiState.Ready

    @Test
    fun `cold start with the lock enabled is locked, and unlock opens it`() = runTest {
        settings.setAppLockEnabled(true)
        val vm = MainActivityViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertTrue(vm.ready().locked)
        vm.unlock()
        assertFalse(
            vm.uiState.first {
                (it as? MainUiState.Ready)?.locked == false
            }.let { (it as MainUiState.Ready).locked },
        )
    }

    @Test
    fun `switching the lock on in settings does not lock the user out right away`() = runTest {
        val vm = MainActivityViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertFalse(vm.ready().locked)

        settings.setAppLockEnabled(true)

        val state = vm.uiState.first {
            (it as? MainUiState.Ready)?.settings?.appLockEnabled == true
        } as MainUiState.Ready
        assertFalse(state.locked)
    }

    @Test
    fun `coming back after the timeout locks, a quick return does not`() = runTest {
        settings.setAppLockEnabled(true)
        settings.setAppLockTimeoutSeconds(30)
        val vm = MainActivityViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.uiState.first { (it as? MainUiState.Ready)?.settings?.appLockTimeoutSeconds == 30 }
        vm.unlock()

        vm.onAppBackground(nowMs = 0)
        vm.onAppForeground(nowMs = 10_000)
        assertFalse((vm.uiState.value as MainUiState.Ready).locked)

        vm.onAppBackground(nowMs = 20_000)
        vm.onAppForeground(nowMs = 60_000)
        assertTrue(vm.uiState.first { (it as MainUiState.Ready).locked }.let { (it as MainUiState.Ready).locked })
    }

    @Test
    fun `a picker trip does not lock`() = runTest {
        settings.setAppLockEnabled(true)
        val vm = MainActivityViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.ready()
        vm.unlock()

        vm.onLaunchingForResult()
        vm.onAppBackground(nowMs = 0)
        vm.onAppForeground(nowMs = 120_000)

        assertFalse((vm.uiState.value as MainUiState.Ready).locked)
    }
}
