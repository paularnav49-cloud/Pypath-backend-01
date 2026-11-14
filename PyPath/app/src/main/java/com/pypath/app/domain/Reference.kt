package com.pypath.app.domain

import com.pypath.app.data.model.GlossaryTerm

/** Glossary search: pure and testable. */
object GlossarySearch {

    /**
     * Terms matching [query] (case-insensitive) in the term, an alias or the definition, optionally only
     * from [level]. With a query, results are ranked: term starts with the query, then term contains it,
     * then an alias matches, then the definition. Without a query, terms are sorted A-Z.
     */
    fun filter(terms: List<GlossaryTerm>, query: String, level: Int? = null): List<GlossaryTerm> {
        val pool = if (level == null) terms else terms.filter { it.level == level }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return pool.sortedBy { sortKey(it.term) }
        return pool.mapNotNull { t ->
            val name = t.term.lowercase()
            val rank = when {
                name.startsWith(q) || sortKey(t.term).startsWith(q) -> 0
                q in name -> 1
                t.aliases.any { q in it.lowercase() } -> 2
                q in t.definition.lowercase() -> 3
                else -> return@mapNotNull null
            }
            rank to t
        }.sortedWith(compareBy({ it.first }, { sortKey(it.second.term) })).map { it.second }
    }

    /** Sort "f-string" under f and "print()" under p, ignoring leading symbols and case. */
    fun sortKey(term: String): String = term.lowercase().trimStart { !it.isLetterOrDigit() }
}
