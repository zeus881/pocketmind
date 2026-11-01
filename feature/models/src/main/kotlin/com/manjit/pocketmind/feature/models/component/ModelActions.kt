package com.manjit.pocketmind.feature.models.component

/** All callbacks of a model card, grouped so composables don't need 5 lambda parameters. */
data class ModelActions(
    val onDownload: (String) -> Unit,
    val onPause: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onSelect: (String) -> Unit,
)
