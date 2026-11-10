package com.sanjay.pocketmind.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces `Dispatchers.Main` with a test dispatcher.
 *
 * WHY: `viewModelScope` runs on `Dispatchers.Main`, which is the Android UI thread. On a
 * plain JVM (unit tests) there is no UI thread, so ViewModel code would crash with
 * "Module with the Main dispatcher is missing". The rule swaps in a test dispatcher before
 * each test and restores it afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
