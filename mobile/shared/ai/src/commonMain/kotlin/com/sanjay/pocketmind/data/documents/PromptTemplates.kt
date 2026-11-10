package com.sanjay.pocketmind.data.documents

/**
 * All task prompts in one place, as pure functions (unit-tested).
 *
 * Small models follow SHORT, STRICT, EXPLICIT rules much better than polite open requests.
 * Each template says exactly what to output and what NOT to add.
 *
 * Note: prompts are built by joining lines, NOT with `"""...$text...""".trimIndent()`.
 * trimIndent() removes the smallest common indentation; inserted document text has none, so it
 * would silently remove nothing and every instruction line would keep its leading spaces.
 */
object PromptTemplates {
    const val SUMMARY_SYSTEM = "You summarize documents accurately. You never invent facts that are not in the text."

    const val TRANSLATE_SYSTEM = "You are a professional translator. You output only the translation."

    /** MAP step: one chunk → a few bullets. */
    fun mapPrompt(chunk: String, part: Int, totalParts: Int): String = lines(
        "Summarize part $part of $totalParts of a document in 3 to 5 short bullet points.",
        "Rules: use only facts from the text, keep numbers and names exactly, no introduction.",
        "Text:",
        chunk,
    )

    /** REDUCE step: many partial summaries → one summary (final) or a shorter list (intermediate). */
    fun reducePrompt(partSummaries: List<String>, isFinal: Boolean): String = lines(
        if (isFinal) {
            "Write the final summary of the whole document: a 2-sentence overview, then 5 to 8 bullet points."
        } else {
            "Combine these partial summaries into one shorter list of at most 8 bullet points."
        },
        "Rules: remove repetition, keep the most important facts, numbers and names, do not add new facts.",
        "Partial summaries:",
        partSummaries.mapIndexed { i, s -> "Part ${i + 1}:\n$s" }.joinToString("\n\n"),
    )

    /** One-pass summary for short documents that fit in a single chunk. */
    fun singlePassPrompt(text: String): String = lines(
        "Summarize the document below: a 2-sentence overview, then 5 to 8 bullet points.",
        "Rules: use only facts from the text, keep numbers and names exactly.",
        "Document:",
        text,
    )

    /** Strict translation template (CLAUDE.md: fixed rules give better results from small models). */
    fun translatePrompt(text: String, from: String, to: String): String = lines(
        "Translate the text from $from to $to.",
        "Rules: output only the translation, keep names and numbers unchanged, keep line breaks.",
        "Text:",
        text,
    )

    private fun lines(vararg parts: String) = parts.joinToString("\n")
}
