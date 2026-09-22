package org.nsh07.wikireader.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PsychonautWiki runs MediaWiki, so it answers the app's existing queries — but it has no
 * Wikidata, so it returns no descriptions and no search snippets. The JSON here is the real
 * response to the queries [org.nsh07.wikireader.network.WikipediaApiService] sends.
 */
class PsychonautWikiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String) =
        javaClass.classLoader!!.getResourceAsStream("psychonautwiki/$name")!!
            .bufferedReader().readText()

    @Test
    fun hostForPsychonautWiki_isNotAWikipediaSubdomain() {
        assertEquals("psychonautwiki.org", wikiHost(PSYCHONAUT_WIKI_LANG))
        assertEquals("en.wikipedia.org", wikiHost("en"))
        assertEquals("de.wikipedia.org", wikiHost("de"))
    }

    @Test
    fun articleUrl_pointsAtTheRightWiki() {
        assertEquals(
            "https://psychonautwiki.org/wiki/LSD",
            wikiArticleUrl(PSYCHONAUT_WIKI_LANG, "LSD")
        )
        assertEquals("https://en.wikipedia.org/wiki/Earth", wikiArticleUrl("en", "Earth"))
    }

    @Test
    fun theSourceIsNamedNotShownAsACode() {
        assertEquals("PsychonautWiki", langCodeToName(PSYCHONAUT_WIKI_LANG))
        // Ordinary languages are untouched.
        assertEquals("English", langCodeToName("en"))
    }

    @Test
    fun isPsychonautWiki_onlyMatchesTheReservedCode() {
        assertTrue(isPsychonautWiki(PSYCHONAUT_WIKI_LANG))
        assertEquals(false, isPsychonautWiki("en"))
        assertEquals(false, isPsychonautWiki(null))
    }

    @Test
    fun prefixSearchResponse_deserialises() {
        val results = json.decodeFromString<WikiApiPrefixSearchResults>(resource("prefix.json"))
            .query.pages

        assertTrue(results.isNotEmpty())
        val lsd = results.first { it.title == "LSD" }
        assertNotNull(lsd.thumbnail)
        // No Wikidata, so no description — the field has to tolerate being absent.
        assertNull(lsd.terms)
    }

    @Test
    fun searchResponse_deserialisesWithoutSnippets() {
        val results = json.decodeFromString<WikiApiSearchResults>(resource("search.json"))
            .query.pages

        assertTrue(results.isNotEmpty())
        // PsychonautWiki returns no snippet or titlesnippet; without defaults this threw.
        results.forEach {
            assertNotNull(it.snippet)
            assertNotNull(it.titleSnippet)
        }
    }

    @Test
    fun taggingAResult_marksWhereItCameFrom() {
        val result = json.decodeFromString<WikiApiPrefixSearchResults>(resource("prefix.json"))
            .query.pages.first()

        assertNull("results default to the wiki being read", result.lang)
        assertEquals(PSYCHONAUT_WIKI_LANG, result.copy(lang = PSYCHONAUT_WIKI_LANG).lang)
    }
}
