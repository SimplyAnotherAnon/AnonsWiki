package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PsychonautWiki runs Semantic MediaWiki, which annotates links as `[[property::value]]`. Only
 * the value is shown; the property is metadata.
 */
class SemanticLinkTest {

    private fun render(wikitext: String) = wikitext.toWikitextAnnotatedString(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        loadPage = {},
        fontSize = 16,
        showRef = {}
    ).text

    @Test
    fun annotatedLink_showsOnlyTheValue() {
        assertEquals(
            "a classical psychedelic substance of the lysergamide class",
            render(
                "a [[classical psychedelics|classical]] [[psychoactive class::psychedelic]] " +
                        "substance of the [[chemical class::lysergamide]] class"
            )
        )
    }

    @Test
    fun annotatedLinkWithALabel_showsTheLabel() {
        assertEquals("see Stimulation", render("see [[Effect::Stimulation|Stimulation]]"))
    }

    @Test
    fun ordinaryLinksAreUnchanged() {
        assertEquals("Earth", render("[[Earth]]"))
        assertEquals("the planet", render("[[Earth|the planet]]"))
        assertEquals("Category:Science", render("[[:Category:Science]]"))
    }
}
