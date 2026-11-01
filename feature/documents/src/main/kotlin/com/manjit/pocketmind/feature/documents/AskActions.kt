package com.manjit.pocketmind.feature.documents

/** Callbacks of the Ask pane, grouped so composables don't need seven lambda parameters. */
data class AskActions(
    val onAddPdf: () -> Unit,
    val onSelect: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onQuestionChange: (String) -> Unit,
    val onAsk: () -> Unit,
    val onStop: () -> Unit,
    val onOpenModels: () -> Unit,
)
