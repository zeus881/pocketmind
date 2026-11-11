package com.sanjay.pocketmind.feature.personas.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.feature.personas.PersonasScreen
import kotlinx.serialization.Serializable

/** Personas & prompt library (opened from Settings). */
@Serializable
data object PersonasRoute

fun NavController.navigateToPersonas() = navigate(PersonasRoute)

fun NavGraphBuilder.personasScreen(onBack: () -> Unit) {
    composable<PersonasRoute> { PersonasScreen(onBack = onBack) }
}
