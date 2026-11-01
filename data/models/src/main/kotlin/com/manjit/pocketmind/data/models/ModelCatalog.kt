package com.manjit.pocketmind.data.models

import android.content.Context
import com.manjit.pocketmind.core.model.LlmModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * The list of downloadable models, read from `assets/models.json` (shipped inside the APK).
 *
 * WHY a JSON asset and not Kotlin code? The catalog is data, not logic: it's easy to review,
 * easy to validate in a unit test, and in v2 the same file format could come from a remote
 * config without changing any code.
 */
@Singleton
class ModelCatalog @Inject constructor(@ApplicationContext private val context: Context) {

    val models: List<LlmModelInfo> by lazy {
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
    }

    fun find(id: String): LlmModelInfo? = models.firstOrNull { it.id == id }

    companion object {
        const val ASSET_NAME = "models.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): List<LlmModelInfo> = json.decodeFromString(text)
    }
}
