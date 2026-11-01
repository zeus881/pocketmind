package com.manjit.pocketmind.feature.benchmark.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.benchmark.BenchmarkScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Benchmark screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object BenchmarkRoute

fun NavController.navigateToBenchmark(navOptions: NavOptions? = null) = navigate(BenchmarkRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.benchmarkScreen() {
    composable<BenchmarkRoute> {
        BenchmarkScreen()
    }
}
