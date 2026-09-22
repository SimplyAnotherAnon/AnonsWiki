package org.nsh07.wikireader.ui.homeScreen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.nsh07.wikireader.data.PSYCHONAUT_WIKI_LANG
import org.nsh07.wikireader.data.WikiPrefixSearchResult
import org.nsh07.wikireader.data.WikiSearchResult

/**
 * Results from several wikis share one lazy list, and a lazy list rejects duplicate keys with an
 * exception rather than a visual glitch — so a title present on both wikis crashed the search.
 */
class SearchResultKeysTest {

    @Test
    fun sameTitleOnTwoWikis_getsDistinctKeys() {
        // Searching "LSD" returns a page of that name from Wikipedia and from PsychonautWiki.
        assertNotEquals(
            searchResultKey(null, "LSD", "prefix"),
            searchResultKey(PSYCHONAUT_WIKI_LANG, "LSD", "prefix")
        )
    }

    @Test
    fun theSameResultAlwaysGetsTheSameKey() {
        assertEquals(
            searchResultKey(PSYCHONAUT_WIKI_LANG, "LSD", "prefix"),
            searchResultKey(PSYCHONAUT_WIKI_LANG, "LSD", "prefix")
        )
    }

    @Test
    fun suggestionsAndFullResultsDoNotCollide() {
        assertNotEquals(
            searchResultKey(null, "LSD", "prefix"),
            searchResultKey(null, "LSD", "search")
        )
    }

    @Test
    fun aMergedSuggestionListHasNoDuplicateKeys() {
        val merged =
            listOf(
                WikiPrefixSearchResult(pageId = 7289, title = "LSD", index = 1, lang = PSYCHONAUT_WIKI_LANG),
                WikiPrefixSearchResult(pageId = 9972, title = "LSD/Summary", index = 2, lang = PSYCHONAUT_WIKI_LANG),
            ) + listOf(
                WikiPrefixSearchResult(pageId = 18042, title = "LSD", index = 1),
                WikiPrefixSearchResult(pageId = 66017, title = "LSD (disambiguation)", index = 2),
            )

        val keys = merged.map { searchResultKey(it.lang, it.title, "prefix") }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun aMergedResultListHasNoDuplicateKeys() {
        val merged =
            listOf(WikiSearchResult(title = "LSD", pageId = 7289, index = 1, lang = PSYCHONAUT_WIKI_LANG)) +
                    listOf(WikiSearchResult(title = "LSD", pageId = 18042, index = 1))

        val keys = merged.map { searchResultKey(it.lang, it.title, "search") }
        assertEquals(keys.size, keys.distinct().size)
    }
}
