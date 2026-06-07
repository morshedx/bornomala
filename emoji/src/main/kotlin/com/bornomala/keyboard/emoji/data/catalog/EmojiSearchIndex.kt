package com.bornomala.keyboard.emoji.data.catalog

import com.bornomala.keyboard.emoji.domain.model.Emoji

/**
 * Prebuilt search index over the bundled catalog.
 *
 * Built once (lazily, off the Main thread by the repository) and reused. Each emoji
 * gets a lowercased, space-joined "haystack" of its name plus keywords so matching is
 * a single [String.contains] / token-prefix scan with no per-query allocation of new
 * keyword structures.
 *
 * Ranking favors, in order: exact name match, name token starting with the query,
 * name containing the query, then keyword matches. Original catalog order breaks ties,
 * giving stable, predictable results.
 */
internal class EmojiSearchIndex(catalog: List<Emoji>) {

    private class Entry(
        val emoji: Emoji,
        val nameLower: String,
        val nameTokens: List<String>,
        val keywordsLower: List<String>,
    )

    private val entries: List<Entry> = catalog.map { e ->
        val nameLower = e.name.lowercase()
        Entry(
            emoji = e,
            nameLower = nameLower,
            nameTokens = nameLower.split(' ').filter { it.isNotEmpty() },
            keywordsLower = e.keywords.map { it.lowercase() },
        )
    }

    /**
     * Returns matching emoji ranked best-first. Blank queries return an empty list.
     */
    fun search(query: String): List<Emoji> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        // Collect (entry, score). Lower score = better match.
        val matched = ArrayList<Pair<Emoji, Int>>()
        for (entry in entries) {
            val score = scoreOf(entry, q)
            if (score != NO_MATCH) {
                matched.add(entry.emoji to score)
            }
        }
        // Stable sort by score; ties keep catalog order because the loop is ordered
        // and ArrayList sort is stable.
        matched.sortBy { it.second }
        return matched.map { it.first }
    }

    private fun scoreOf(entry: Entry, q: String): Int {
        if (entry.nameLower == q) return 0
        for (token in entry.nameTokens) {
            if (token == q) return 1
        }
        for (token in entry.nameTokens) {
            if (token.startsWith(q)) return 2
        }
        if (entry.nameLower.contains(q)) return 3
        for (kw in entry.keywordsLower) {
            if (kw == q) return 4
        }
        for (kw in entry.keywordsLower) {
            if (kw.startsWith(q)) return 5
        }
        for (kw in entry.keywordsLower) {
            if (kw.contains(q)) return 6
        }
        return NO_MATCH
    }

    private companion object {
        const val NO_MATCH = Int.MAX_VALUE
    }
}
