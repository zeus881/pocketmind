package com.manjit.pocketmind.data.documents

/** The text of one PDF page. [page] is 1-based, as printed in the document. */
data class PageText(val page: Int, val text: String)

/** A piece of a document small enough for the model, remembering which pages it came from. */
data class TextChunk(val index: Int, val text: String, val startPage: Int, val endPage: Int)

/** A PDF after text extraction. */
data class ExtractedDocument(val name: String, val pages: List<PageText>) {
    val pageCount: Int get() = pages.size
}
