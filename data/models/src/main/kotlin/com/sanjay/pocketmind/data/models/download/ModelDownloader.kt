package com.sanjay.pocketmind.data.models.download

import com.sanjay.pocketmind.core.common.dispatchers.Dispatcher
import com.sanjay.pocketmind.core.common.dispatchers.PmDispatchers
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.data.models.Sha256
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

/**
 * Downloads one model file, resumably and safely.
 *
 * ```
 * model.part exists? ──yes──▶ hash its bytes, send "Range: bytes=<size>-"
 *        │                       ├─ 206 Partial Content → append to the file
 *        no                      └─ 200 OK (server ignored Range) → start over
 *        ▼
 *   GET from byte 0 ──▶ stream 1 MiB at a time: write + update SHA-256 + report progress
 *        ▼
 *   size == expected?  SHA-256 == catalog?  ──yes──▶ atomic rename to model.litertlm ✔
 *        └─no: size → keep .part for a later resume │ hash → delete .part (corrupt) ✘
 * ```
 * The SHA-256 is computed WHILE writing, so there is no second 2.6 GB read at the end.
 */
class ModelDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val files: ModelFiles,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) {
    /** Called with (bytes on disk, total bytes) at most every [PROGRESS_INTERVAL_MS]. */
    fun interface ProgressListener {
        suspend fun onProgress(downloaded: Long, total: Long)
    }

    suspend fun download(model: LlmModelInfo, listener: ProgressListener): AppResult<File> = withContext(io) {
        val part = files.partFile(model.id).apply { parentFile?.mkdirs() }
        if (part.length() > model.sizeBytes) part.delete() // can't be right: start over

        val digest = Sha256.newDigest()
        var downloaded = part.takeIf { it.isFile }?.length() ?: 0L
        // Resuming: the checksum must cover the bytes we already have.
        if (downloaded > 0) Sha256.update(digest, part, downloaded)

        try {
            if (downloaded < model.sizeBytes) {
                downloaded = transfer(model, part, digest, downloaded, listener)
            }
        } catch (e: IOException) {
            // Network dropped: keep model.part so the next attempt resumes from the last byte.
            Timber.w(e, "Download of %s interrupted at %d bytes", model.id, part.length())
            return@withContext AppResult.Failure(ErrorKind.NETWORK, e)
        }

        if (downloaded != model.sizeBytes) {
            return@withContext AppResult.Failure(ErrorKind.NETWORK, IOException("Incomplete: $downloaded bytes"))
        }
        if (!Sha256.matches(Sha256.hex(digest.digest()), model.sha256)) {
            part.delete() // corrupted or tampered: never keep it
            return@withContext AppResult.Failure(ErrorKind.CHECKSUM_MISMATCH)
        }
        // Atomic rename: other code sees either no final file or the complete, verified file.
        val target = files.finalFile(model.id)
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        AppResult.Success(target)
    }

    /** Streams the remaining bytes into [part]. Returns the total bytes on disk afterwards. */
    private suspend fun transfer(
        model: LlmModelInfo,
        part: File,
        digest: MessageDigest,
        alreadyDownloaded: Long,
        listener: ProgressListener,
    ): Long {
        val request = Request.Builder().url(model.url).apply {
            if (alreadyDownloaded > 0) header("Range", "bytes=$alreadyDownloaded-")
        }.build()

        client.newCall(request).execute().use { response ->
            val start = startOffset(response, alreadyDownloaded, digest)
            val downloaded = FileOutputStream(part, start > 0).use { out ->
                copyAndHash(response.body.byteStream(), out, digest, start) { done ->
                    listener.onProgress(done, model.sizeBytes)
                }.also { out.fd.sync() } // make sure bytes are really on disk before we verify/rename
            }
            listener.onProgress(downloaded, model.sizeBytes)
            return downloaded
        }
    }

    /**
     * Copies [input] to [out] in 1 MiB chunks, feeding every chunk into [digest] and reporting
     * progress at most every [PROGRESS_INTERVAL_MS]. Returns the new total byte count.
     */
    private suspend fun copyAndHash(
        input: InputStream,
        out: OutputStream,
        digest: MessageDigest,
        startBytes: Long,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = startBytes
        var lastReport = 0L
        while (true) {
            currentCoroutineContext().ensureActive() // pause/cancel stops promptly; .part stays
            val read = input.read(buffer)
            if (read < 0) return total
            out.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            total += read
            val now = System.currentTimeMillis()
            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now
                onProgress(total)
            }
        }
    }

    /** Decides where writing starts based on how the server answered our Range request. */
    private fun startOffset(response: Response, alreadyDownloaded: Long, digest: MessageDigest): Long = when {
        response.code == HTTP_PARTIAL && alreadyDownloaded > 0 -> alreadyDownloaded

        response.isSuccessful -> {
            // 200 OK: the server sent the whole file from byte 0. Forget the partial data.
            digest.reset()
            0L
        }

        else -> throw IOException("HTTP ${response.code}")
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20 // 1 MiB
        const val PROGRESS_INTERVAL_MS = 500L
        const val HTTP_PARTIAL = 206
    }
}
