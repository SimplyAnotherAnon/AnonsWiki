package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Markup that used to reach the reader verbatim. */
class MarkupLeakTest {

    @Before
    fun clean() = ReferenceData.reset()

    private fun render(wikitext: String) = wikitext.toWikitextAnnotatedString(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        loadPage = {},
        fontSize = 16,
        showRef = {}
    ).text

    @Test
    fun escapeTemplates_becomeTheCharacterTheyStandFor() {
        assertEquals("Caffeine | C8H10N4O2", render("Caffeine {{!}} C8H10N4O2"))
        assertEquals("a || b", render("a {{!!}} b"))
        assertEquals("x = y", render("x {{=}} y"))
    }

    @Test
    fun unknownHtmlTags_areDroppedAndTheirContentKept() {
        // Wikipedia puts invisible anchors in headings.
        assertEquals(
            "Adverse effects",
            render("<span class=\"anchor\" id=\"Side_effects\"></span>Adverse effects")
        )
        assertEquals("a b c", render("a <span>b</span> c"))
        assertEquals("plain", render("<div style=\"float:right\">plain</div>"))
    }

    @Test
    fun externalLinkLabels_areParsedAsWikitext() {
        // A citation builds its link label from the article title, so an unparsed label showed
        // the reader the italic quotes and escaped pipes the title contains.
        val out = render("[https://example.org ''Caffeine {{!}} C8H10N4O2'']")

        assertFalse("markup leaked: $out", out.contains("''"))
        assertFalse("markup leaked: $out", out.contains("{{"))
        assertTrue(out.contains("Caffeine | C8H10N4O2"))
    }

    @Test
    fun aCitationWithAUrl_rendersItsTitleWithoutMarkup() {
        render(
            "Fact.<ref>{{cite web |title=Caffeine {{!}} C8H10N4O2 " +
                    "|website=pubchem.ncbi.nlm.nih.gov |url=https://example.org}}</ref>"
        )
        val list = render("{{reflist}}")

        assertFalse("markup leaked: $list", list.contains("''"))
        assertFalse("markup leaked: $list", list.contains("{{"))
        assertTrue(list.contains("Caffeine | C8H10N4O2"))
        assertTrue(list.contains("pubchem.ncbi.nlm.nih.gov"))
    }

    @Test
    fun knownTagsStillWork() {
        assertEquals("H2O", render("H<sub>2</sub>O"))
        assertEquals("x2", render("x<sup>2</sup>"))
        assertEquals("kept", render("<nowiki>kept</nowiki>"))
    }

    @Test
    fun comparisonInProse_isNotMistakenForATag() {
        assertEquals("a < b and c > d", render("a < b and c > d"))
    }
}
