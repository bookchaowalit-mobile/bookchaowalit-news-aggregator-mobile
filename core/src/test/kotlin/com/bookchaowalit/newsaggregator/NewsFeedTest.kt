package com.bookchaowalit.newsaggregator

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsFeedTest {
    private val t0 = Instant.parse("2026-09-30T10:00:00Z")
    private fun a(source: String, title: String, url: String, minutesAgo: Long, summary: String = "") =
        Article(source, title, url, t0.minusSeconds(minutesAgo * 60), summary)

    @Test
    fun canonicalUrlStripsTrackingAndNoise() {
        assertEquals(
            "https://example.com/story?a=1&b=2",
            NewsFeed.canonicalUrl("HTTPS://www.Example.com:443/story/?b=2&utm_source=x&a=1&fbclid=abc#top"),
        )
        assertEquals("http://example.com:8080/x", NewsFeed.canonicalUrl("http://example.com:8080/x"))
        assertEquals("not a url", NewsFeed.canonicalUrl(" not a url "))
    }

    @Test
    fun mergeDeduplicatesByUrlAndTitleKeepingEarliest() {
        val feedA = listOf(
            a("A", "Kotlin 2.1 released!", "https://news.example/kotlin?utm_medium=rss", 30),
            a("A", "Weather today", "https://news.example/weather", 5),
        )
        val feedB = listOf(
            a("B", "Kotlin 2.1 Released", "https://other.example/k21", 60), // same title, earlier
            a("B", "Something else", "https://www.news.example/weather/", 1), // same URL, later
        )
        val merged = NewsFeed.merge(listOf(feedA, feedB))
        assertEquals(listOf("Weather today", "Kotlin 2.1 Released"), merged.map { it.title })
        assertEquals("B", merged[1].source)
    }

    @Test
    fun filtersBySourceKeywordMuteAndSince() {
        val items = listOf(
            a("A", "Android news", "https://x/1", 10, "Compose update"),
            a("B", "iOS news", "https://x/2", 20, "SwiftUI update"),
            a("A", "Crypto crash", "https://x/3", 30),
        )
        assertEquals(listOf("https://x/1", "https://x/3"), NewsFeed.filter(items, FeedQuery(sources = setOf("A"))).map { it.url })
        assertEquals(listOf("https://x/1", "https://x/2"), NewsFeed.filter(items, FeedQuery(keyword = "UPDATE")).map { it.url })
        assertEquals(2, NewsFeed.filter(items, FeedQuery(mutedKeywords = setOf("crypto", " "))).size)
        assertEquals(listOf("https://x/1"), NewsFeed.filter(items, FeedQuery(since = t0.minusSeconds(15 * 60))).map { it.url })
    }

    @Test
    fun groupsByLocalDay() {
        val bangkok = ZoneId.of("Asia/Bangkok")
        val late = Article("A", "late", "https://x/a", Instant.parse("2026-09-30T18:00:00Z")) // Oct 1 01:00 in Bangkok
        val early = Article("A", "early", "https://x/b", Instant.parse("2026-09-30T01:00:00Z"))
        val groups = NewsFeed.groupByDay(listOf(late, early), bangkok)
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30)), groups.keys.toList())
    }

    @Test
    fun relativeTimeLabels() {
        val z = ZoneOffset.UTC
        assertEquals("just now", NewsFeed.relativeTime(t0.plusSeconds(30), t0, z))
        assertEquals("just now", NewsFeed.relativeTime(t0.minusSeconds(59), t0, z))
        assertEquals("5m ago", NewsFeed.relativeTime(t0.minusSeconds(300), t0, z))
        assertEquals("3h ago", NewsFeed.relativeTime(t0.minusSeconds(3 * 3600), t0, z))
        assertEquals("2d ago", NewsFeed.relativeTime(t0.minusSeconds(2 * 86400), t0, z))
        assertEquals("2026-09-20", NewsFeed.relativeTime(t0.minusSeconds(10 * 86400), t0, z))
    }
}
