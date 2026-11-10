package com.sanjay.pocketmind.core.speech

/**
 * Turns a Markdown answer into text that sounds right when read aloud. Pure, unit-tested.
 *
 * Reading Markdown literally would say "asterisk asterisk" and spell out code. Instead:
 * code blocks become a short spoken note, links keep only their label, and formatting
 * characters (#, *, _, `, >) disappear.
 */
object SpeechText {
    fun fromMarkdown(markdown: String, codeBlockNote: String = "Code block omitted."): String = markdown
        .replace(Regex("```[\\s\\S]*?```"), " $codeBlockNote ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1") // [label](url) → label
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "") // headings
        .replace(Regex("(?m)^\\s*>\\s?"), "") // quotes
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "") // bullets
        .replace(Regex("[*_`~]"), "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()

    /**
     * Splits [text] into pieces of at most [maxLength] characters, preferring sentence ends, then
     * spaces: text-to-speech engines refuse inputs longer than their limit (often 4,000).
     */
    fun chunks(text: String, maxLength: Int): List<String> {
        require(maxLength > 0)
        val pieces = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > maxLength) {
            val window = rest.substring(0, maxLength)
            val sentenceEnd = listOf(". ", "? ", "! ", "\n").maxOf { window.lastIndexOf(it) }
            val cut = sentenceEnd
                .takeIf { it > 0 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: maxLength
            pieces += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }
}

/** Maps android.speech.SpeechRecognizer error codes (plain ints, so this stays pure). */
object SpeechErrors {
    private const val ERROR_SPEECH_TIMEOUT = 6
    private const val ERROR_NO_MATCH = 7
    private const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE = 13

    fun map(code: Int): SpeechError = when (code) {
        ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH -> SpeechError.NOTHING_HEARD
        ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PERMISSION
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.UNAVAILABLE
        else -> SpeechError.FAILED
    }
}
