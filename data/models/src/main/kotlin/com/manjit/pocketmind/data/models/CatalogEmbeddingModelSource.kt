package com.manjit.pocketmind.data.models

import com.manjit.pocketmind.core.llm.EmbeddingModelSource
import com.manjit.pocketmind.core.llm.ModelFiles
import com.manjit.pocketmind.core.model.ModelKind
import javax.inject.Inject

/** The embedding model is the catalog entry of kind EMBEDDING; usable once it's downloaded. */
internal class CatalogEmbeddingModelSource @Inject constructor(
    private val catalog: ModelCatalog,
    private val files: ModelFiles,
) : EmbeddingModelSource {
    override fun embeddingModelId(): String? = catalog.models.firstOrNull { it.kind == ModelKind.EMBEDDING }?.id

    override suspend fun embeddingModelPath(): String? =
        embeddingModelId()?.takeIf(files::isDownloaded)?.let { files.finalFile(it).path }
}
