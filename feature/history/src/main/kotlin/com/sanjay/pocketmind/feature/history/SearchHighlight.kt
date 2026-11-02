package com.sanjay.pocketmind.feature.history

/**
 * Where the search words occur in [text], as index ranges to emphasise. Matching is
 * case-insensitive and by word START, the same rule the full-text search uses ("dro" marks
 * "Drone" but not "hydro"), so what is highlighted is what made the chat match.
 * Overlapping matches are merged. Pure function: unit-tested, then drawn by [HistoryScreen].
 */
internal fun searchMatches(text: String, query: String): List<IntRange> {
    val words = query.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
    if (words.isEmpty() || text.isEmpty()) return emptyList()
    val ranges = words.flatMap { word ->
        Regex("(?<![\\p{L}\\p{M}\\p{N}])" + Regex.escape(word), RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.range }
            .toList()
    }.sortedBy { it.first }
    return ranges.fold(mutableListOf()) { merged, range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
        merged
    }
}
