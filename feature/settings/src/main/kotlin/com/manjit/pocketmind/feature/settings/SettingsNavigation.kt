package com.manjit.pocketmind.feature.settings

/** Navigation targets reachable from Settings (wired up by the app module). */
data class SettingsNavigation(val onOpenModels: () -> Unit, val onOpenBenchmark: () -> Unit)
