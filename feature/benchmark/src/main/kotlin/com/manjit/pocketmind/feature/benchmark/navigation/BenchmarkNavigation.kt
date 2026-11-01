package com.manjit.pocketmind.feature.benchmark.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.benchmark.BenchmarkScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the benchmark screen. */
@Serializable
data object BenchmarkRoute

fun NavController.navigateToBenchmark(navOptions: NavOptions? = null) = navigate(BenchmarkRoute, navOptions)

fun NavGraphBuilder.benchmarkScreen(onBack: () -> Unit) {
    composable<BenchmarkRoute> {
        BenchmarkScreen(onBack = onBack)
    }
}
