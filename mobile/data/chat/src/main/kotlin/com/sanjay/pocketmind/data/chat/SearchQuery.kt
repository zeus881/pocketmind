package com.sanjay.pocketmind.data.chat

/**
 * Turns what the user typed in History's search box into the two safe query parts
 * [com.sanjay.pocketmind.core.database.dao.ChatDao.observeSummaries] needs.
 *
 * - [likePattern]: for titles. `%` and `_` are LIKE wildcards and `\` is our escape character,
 *   so a search for "50%" must look for a literal percent sign, not "50 followed by anything".
 * - [ftsQuery]: for message text, an SQLite full-text MATCH expression. Raw user text could
 *   contain FTS syntax (`"`, `*`, `-`, `OR`, `NEAR`) and cause errors or odd results, so only
 *   letters, combining marks (Hindi vowel signs and virama are marks, not letters) and digits
 *   survive. Each word becomes a quoted prefix term (`"dro*"`: in FTS4 the star goes INSIDE the
 *   quotes; `"dro"*` silently matches nothing), and terms
 *   are ANDed: "drone batt" finds messages containing words starting with both.
 */
data class SearchQuery(val raw: String, val likePattern: String, val ftsQuery: String) {
    companion object {
        fun from(input: String): SearchQuery {
            val raw = input.trim()
            val like = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val words = raw.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
            val fts = words.joinToString(" ") { "\"$it*\"" }
            return SearchQuery(raw = raw, likePattern = like, ftsQuery = fts)
        }
    }
}
