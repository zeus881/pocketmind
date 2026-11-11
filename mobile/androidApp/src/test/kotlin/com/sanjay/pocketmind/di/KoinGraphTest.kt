package com.sanjay.pocketmind.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Every definition in the app's Koin graph can be built: each constructor parameter of each class has a
 * definition. Hilt checked this at compile time in v2; with Koin (V3) this test is the guard, so a missing
 * binding fails CI instead of crashing a screen on the phone.
 *
 * Extra types are supplied by Android or Koin at run time, not by our modules: the Context, a ViewModel's
 * SavedStateHandle, WorkManager's WorkerParameters, and the dispatchers/scope that are defined under a name
 * (`PmQualifiers`), which `verify` can't see through.
 */
@OptIn(KoinExperimentalAPI::class) // verify() is marked experimental in Koin 4.2.2; it only reads the module graph
class KoinGraphTest {
    @Test
    fun everyDefinitionCanBeBuilt() {
        module { includes(pocketMindModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                CoroutineDispatcher::class,
                CoroutineScope::class,
            ),
        )
    }
}
