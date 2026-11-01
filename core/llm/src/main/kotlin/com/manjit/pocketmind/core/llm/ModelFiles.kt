package com.manjit.pocketmind.core.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-disk layout of downloaded models, in one place:
 * ```
 * filesDir/models/<id>/model.part      ← being downloaded (may be partial)
 * filesDir/models/<id>/model.litertlm  ← complete AND checksum-verified
 * ```
 * `filesDir` is app-private internal storage: other apps can't read it, it isn't backed up
 * (see data_extraction_rules.xml) and Android deletes it on uninstall.
 *
 * A file only ever gets the final name after its SHA-256 matched, so "the final file exists"
 * means "the model is complete and correct".
 */
@Singleton
class ModelFiles internal constructor(private val root: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "models"))

    fun directory(modelId: String): File = File(root, modelId)

    fun partFile(modelId: String): File = File(directory(modelId), PART_NAME)

    fun finalFile(modelId: String): File = File(directory(modelId), FINAL_NAME)

    fun isDownloaded(modelId: String): Boolean = finalFile(modelId).isFile

    /** Bytes already downloaded into the partial file (0 if none). */
    fun partialBytes(modelId: String): Long = partFile(modelId).takeIf { it.isFile }?.length() ?: 0L

    /** Deletes everything for a model (complete and partial). */
    fun delete(modelId: String): Boolean = directory(modelId).deleteRecursively()

    /** The folder where free space must be checked before a download. */
    fun storageRoot(): File = root.apply { mkdirs() }

    companion object {
        const val PART_NAME = "model.part"
        const val FINAL_NAME = "model.litertlm"

        /** For tests: a layout rooted in any folder. */
        fun forTesting(root: File) = ModelFiles(root)
    }
}
