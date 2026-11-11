package com.sanjay.pocketmind.data.documents

import com.sanjay.pocketmind.core.common.result.AppResult

/**
 * Reads the text of a PDF chosen with the system file picker. [uri] is the picked location as text (a `content://`
 * URI on Android, a file URL on iOS), so this interface and the screens using it are multiplatform.
 */
interface PdfTextExtractor {
    suspend fun extract(uri: String): AppResult<ExtractedDocument>
}
