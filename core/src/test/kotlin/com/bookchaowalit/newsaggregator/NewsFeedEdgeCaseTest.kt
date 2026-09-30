package com.bookchaowalit.newsaggregator

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewsFeedEdgeCaseTest {
    private val t0 = Instant.parse("2026-09-30T10:00:00Z")
    private fun at(source: String, title: String, url: String, hoursAgo: Long) =
        Article(source, title, url, t0.minusSeconds(hoursAgo * 3600))

    @Test
    fun sameSourceRepeatingAHeadlineIsNotADuplicate() {
        val feed = listOf(
            at("A", "Morning briefing", "https://a.example/brief-1", 48),
            at("A", "Morning briefing", "https://a.example/brief-2", 24),
            at("A", "Morning briefing", "https://a.example/brief-3", 0),
        )
        assertEquals(3, NewsFeed.merge(listOf(feed)).size)
    }

    @Test
    fun crossSourceTitleDuplicatesOnlyWithinTheWindow() {
        val merged = NewsFeed.merge(
            listOf(
                listOf(at("A", "Election results", "https://a.example/1", 100)),
                listOf(
                    at("B", "Election Results!", "https://b.example/1", 90),  // 10h later: duplicate
                    at("B", "Election results", "https://b.example/2", 0),    // 100h later: new story
                ),
            ),
        )
        assertEquals(listOf("https://b.example/2", "https://a.example/1"), merged.map { it.url })
    }

    @Test
    fun emptyTitlesNeverCollideAndEmptyInputIsFine() {
        val merged = NewsFeed.merge(listOf(listOf(at("A", "", "https://a/1", 1)), listOf(at("B", "!!!", "https://b/1", 1))))
        assertEquals(2, merged.size)
        assertTrue(NewsFeed.merge(emptyList()).isEmpty())
    }

    @Test
    fun mutedTermsMatchWholeWordStartsNotInsideWords() {
        val items = listOf(
            at("A", "He said it rained", "https://x/1", 1),
            at("A", "AI's new model", "https://x/2", 1),
            at("A", "Open-AI rivals", "https://x/3", 1),
        )
        assertEquals(listOf("https://x/1"), NewsFeed.filter(items, FeedQuery(mutedKeywords = setOf(" AI "))).map { it.url })
        assertEquals(listOf("https://x/2", "https://x/3"), NewsFeed.filter(items, FeedQuery(keyword = "ai")).map { it.url })
    }

    @Test
    fun thaiAndCjkTermsUseSubstringMatch() {
        assertTrue(NewsFeed.matchesTerm("ราคาน้ำมันวันนี้", "น้ำมัน"))
        assertTrue(NewsFeed.matchesTerm("東京の天気", "天気"))
        assertFalse(NewsFeed.matchesTerm("anything", "  "))
    }

    @Test
    fun canonicalUrlEdgeCases() {
        assertEquals("https://example.com", NewsFeed.canonicalUrl("https://example.com/"))
        assertEquals("https://example.com/a?id=1", NewsFeed.canonicalUrl("https://example.com/a?UTM_Source=x&id=1&&ref=y"))
        assertEquals("https://example.com:8443/a", NewsFeed.canonicalUrl("https://example.com:8443/a"))
        assertEquals("example.com/a", NewsFeed.canonicalUrl("example.com/a"))       // no scheme: left as is
        assertEquals("http://x y", NewsFeed.canonicalUrl("http://x y"))             // unparsable: left as is
    }

    @Test
    fun relativeTimeBoundaries() {
        val z = ZoneOffset.UTC
        assertEquals("just now", NewsFeed.relativeTime(t0.plusSeconds(600), t0, z)) // future timestamps
        assertEquals("59m ago", NewsFeed.relativeTime(t0.minusSeconds(59 * 60 + 59), t0, z))
        assertEquals("1h ago", NewsFeed.relativeTime(t0.minusSeconds(3600), t0, z))
        assertEquals("6d ago", NewsFeed.relativeTime(t0.minusSeconds(7 * 86400 - 1), t0, z))
        assertEquals("2026-09-23", NewsFeed.relativeTime(t0.minusSeconds(7 * 86400), t0, z))
    }
}
