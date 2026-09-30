package com.bookchaowalit.newsaggregator

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsFeedPass3Test {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun thaiHeadlinesDifferingOnlyInToneMarksAreDifferentStories() {
        // "เสือ" (tiger) vs "เสื่อ" (mat): stripping combining marks made them identical.
        val a = Article("A", "ขาย เสือ", "https://a.example/1", t0)
        val b = Article("B", "ขาย เสื่อ", "https://b.example/2", t0.plusSeconds(60))
        assertEquals(2, NewsFeed.merge(listOf(listOf(a), listOf(b))).size)
        // Genuine duplicates are still merged.
        val c = Article("C", "ขาย เสือ!", "https://c.example/3", t0.plusSeconds(120))
        assertEquals(listOf(b, a), NewsFeed.merge(listOf(listOf(a), listOf(b, c))))
    }

    @Test
    fun articlesWithoutUrlAreNotDuplicatesOfEachOther() {
        val a = Article("A", "First", "", t0)
        val b = Article("B", "Second", "  ", t0.plusSeconds(60))
        assertEquals(listOf(b, a), NewsFeed.merge(listOf(listOf(a), listOf(b))))
    }

    @Test
    fun zeroWidthKeywordDoesNotHideEverything() {
        val a = Article("A", "Hello", "https://a.example/1", t0)
        assertEquals(listOf(a), NewsFeed.filter(listOf(a), FeedQuery(keyword = "​")))
        assertEquals(listOf(a), NewsFeed.filter(listOf(a), FeedQuery(keyword = "﻿hello⁠")))
        assertEquals(emptyList(), NewsFeed.filter(listOf(a), FeedQuery(mutedKeywords = setOf("hel​lo"))))
    }
}
