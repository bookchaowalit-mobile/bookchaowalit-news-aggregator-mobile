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

    /**
     * Merges feeds: drops duplicates (same canonical URL, or same normalised
     * title from another source), keeping the earliest-published copy.
     * Result is newest first.
     */
    fun merge(feeds: List<List<Article>>): List<Article> {
        val seenUrls = HashSet<String>()
        val seenTitles = HashSet<String>()
        return feeds.flatten()
            .sortedBy { it.publishedAt }
            .filter { a ->
                val u = canonicalUrl(a.url)
                val t = titleKey(a.title)
                val fresh = u !in seenUrls && (t.isEmpty() || t !in seenTitles)
                if (fresh) { seenUrls += u; if (t.isNotEmpty()) seenTitles += t }
                fresh
            }
            .sortedByDescending { it.publishedAt }
    }

    fun filter(articles: List<Article>, q: FeedQuery): List<Article> {
        val kw = q.keyword.trim().lowercase()
        val muted = q.mutedKeywords.map { it.lowercase() }.filter { it.isNotBlank() }
        return articles.filter { a ->
            val text = (a.title + " " + a.summary).lowercase()
            (q.sources.isEmpty() || a.source in q.sources) &&
                (kw.isEmpty() || kw in text) &&
                muted.none { it in text } &&
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
