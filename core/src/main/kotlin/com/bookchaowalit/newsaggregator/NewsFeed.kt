package com.bookchaowalit.newsaggregator

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Article(
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val summary: String = "",
)

data class FeedQuery(
    val sources: Set<String> = emptySet(),
    val keyword: String = "",
    val mutedKeywords: Set<String> = emptySet(),
    val since: Instant? = null,
)

object NewsFeed {
    private val TRACKING_PARAMS = setOf("fbclid", "gclid", "ref", "mc_cid", "mc_eid")

    /**
     * Canonical form used for de-duplication: lower-case scheme/host, no
     * `www.`, no fragment, no trailing slash, tracking params (utm_*, fbclid...)
     * removed, remaining params sorted. Returns the trimmed input if it is not a URL.
     */
    fun canonicalUrl(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return raw.trim()
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return raw.trim()
        val scheme = (uri.scheme ?: "https").lowercase()
        val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = uri.rawQuery?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.filterNot { p -> val k = p.substringBefore('=').lowercase(); k.startsWith("utm_") || k in TRACKING_PARAMS }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        return "$scheme://$host$port$path" + if (query.isEmpty()) "" else "?$query"
    }

    private fun titleKey(title: String) = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Two copies of the same headline further apart than this are separate stories (e.g. daily columns). */
    val TITLE_DUPLICATE_WINDOW: Duration = Duration.ofHours(48)

    /**
     * Merges feeds: drops duplicates (same canonical URL, or same normalised
     * title from *another* source within [TITLE_DUPLICATE_WINDOW]), keeping
     * the earliest-published copy. A source repeating its own headline
     * ("Morning briefing") is not a duplicate. Result is newest first.
     */
    fun merge(feeds: List<List<Article>>): List<Article> {
        val seenUrls = HashSet<String>()
        val kept = HashMap<String, MutableList<Article>>() // title key -> kept articles
        return feeds.flatten()
            .sortedBy { it.publishedAt }
            .filter { a ->
                val u = canonicalUrl(a.url)
                val t = titleKey(a.title)
                val sameStory = t.isNotEmpty() && kept[t].orEmpty().any { k ->
                    k.source != a.source && Duration.between(k.publishedAt, a.publishedAt) <= TITLE_DUPLICATE_WINDOW
                }
                val fresh = u !in seenUrls && !sameStory
                if (fresh) {
                    seenUrls += u
                    if (t.isNotEmpty()) kept.getOrPut(t) { mutableListOf() } += a
                }
                fresh
            }
            .sortedByDescending { it.publishedAt }
    }

    /**
     * Keyword match used by [filter]. Latin-style terms match at the start of a
     * word, so muting "ai" does not hide "said" (but does hide "AI's").
     * Terms containing Thai/CJK characters, which are written without spaces,
     * fall back to a plain substring match.
     */
    fun matchesTerm(text: String, term: String): Boolean {
        val t = term.trim().lowercase()
        if (t.isEmpty()) return false
        val haystack = text.lowercase()
        if (t.any { it.code in 0x0E00..0x0E7F || Character.isIdeographic(it.code) || it.code in 0x3040..0x30FF || it.code in 0xAC00..0xD7AF }) {
            return t in haystack
        }
        var from = 0
        while (true) {
            val i = haystack.indexOf(t, from)
            if (i < 0) return false
            if (i == 0 || !haystack[i - 1].isLetterOrDigit()) return true
            from = i + 1
        }
    }

    fun filter(articles: List<Article>, q: FeedQuery): List<Article> {
        val kw = q.keyword.trim()
        val muted = q.mutedKeywords.map { it.trim() }.filter { it.isNotEmpty() }
        return articles.filter { a ->
            val text = a.title + " " + a.summary
            (q.sources.isEmpty() || a.source in q.sources) &&
                (kw.isEmpty() || matchesTerm(text, kw)) &&
                muted.none { matchesTerm(text, it) } &&
                (q.since == null || !a.publishedAt.isBefore(q.since))
        }
    }

    /** Groups (already sorted) articles by local calendar day, preserving order. */
    fun groupByDay(articles: List<Article>, zone: ZoneId): Map<LocalDate, List<Article>> =
        articles.groupBy { it.publishedAt.atZone(zone).toLocalDate() }

    /** "just now", "5m ago", "3h ago", "2d ago", or the ISO date for older items. */
    fun relativeTime(publishedAt: Instant, now: Instant, zone: ZoneId): String {
        val d = Duration.between(publishedAt, now)
        return when {
            d.isNegative || d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()}m ago"
            d.toDays() < 1 -> "${d.toHours()}h ago"
            d.toDays() < 7 -> "${d.toDays()}d ago"
            else -> publishedAt.atZone(zone).toLocalDate().toString()
        }
    }
}
