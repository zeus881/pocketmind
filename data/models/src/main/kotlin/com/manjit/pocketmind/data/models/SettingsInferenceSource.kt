package com.manjit.pocketmind.data.models

import com.manjit.pocketmind.core.datastore.SettingsDataSource
import com.manjit.pocketmind.core.llm.DefaultPrompts
import com.manjit.pocketmind.core.llm.InferenceSettings
import com.manjit.pocketmind.core.llm.InferenceSettingsSource
import com.manjit.pocketmind.core.llm.ModelFiles
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.core.model.ModelKind
import javax.inject.Inject

/**
 * The real [InferenceSettingsSource]: the selected, fully downloaded chat model plus the
 * user's saved settings. Returns null when no usable model exists, so the chat screen can
 * offer "Get a model" instead of failing.
 */
internal class SettingsInferenceSource @Inject constructor(
    private val settings: SettingsDataSource,
    private val catalog: ModelCatalog,
    private val files: ModelFiles,
) : InferenceSettingsSource {
    override suspend fun current(): InferenceSettings? {
        val user = settings.current()
        val modelId = user.selectedModelId
            ?.takeIf { id -> catalog.find(id)?.kind == ModelKind.CHAT && files.isDownloaded(id) }
            ?: return null
        return InferenceSettings(
            modelPath = files.finalFile(modelId).path,
            preferGpu = user.preferGpu,
            systemPrompt = user.customSystemPrompt ?: DefaultPrompts.SYSTEM,
            sampling = SamplingParams(temperature = user.temperature.toDouble(), topK = user.topK),
            modelId = modelId,
            modelName = catalog.find(modelId)?.displayName.orEmpty(),
        )
    }
}
