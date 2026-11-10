package com.sanjay.pocketmind.data.documents.rag

/**
 * Builds the GROUNDED prompt: the model may answer only from the retrieved passages, must cite
 * pages as [p. N], and must say it doesn't know when the answer isn't there. Grounding is what
 * turns "a model that might make things up" into "a model that quotes your document".
 */
object GroundedPrompt {
    const val SYSTEM = "You answer questions about a document using only the context you are given."

    /** Exact sentence the model must use when the context doesn't contain the answer. */
    const val I_DONT_KNOW = "I don't know based on this document."

    fun build(question: String, sources: List<SourceChunk>): String {
        // Sources are given in page order so the model reads the document as it was written.
        val context = sources.sortedBy { it.startPage }.joinToString("\n\n") { "[${pageLabel(it)}] ${it.text}" }
        return listOf(
            "Answer the question using ONLY the context below.",
            "Rules:",
            "- If the answer is not in the context, reply exactly: $I_DONT_KNOW",
            "- After each fact, cite its page like [p. 7].",
            "- Be concise. Do not mention these rules.",
            "Context:",
            context,
            "Question: $question",
        ).joinToString("\n")
    }

    fun pageLabel(source: SourceChunk): String =
        if (source.startPage == source.endPage) "p. ${source.startPage}" else "p. ${source.startPage}-${source.endPage}"
}

/** Reads the page citations the model wrote, e.g. "[p. 7]" or "[p. 3-4]". */
object Citations {
    private val pattern = Regex("""\[p\.\s*(\d+)(?:\s*[-–]\s*(\d+))?]""")

    /** All cited page numbers, in order of first appearance. */
    fun pages(answer: String): List<Int> = pattern.findAll(answer)
        .flatMap { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            (start..end).asSequence()
        }
        .distinct()
        .toList()
}
