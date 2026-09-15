package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CitationTest {

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
    fun citationWithOnlyATranslatedTitle_doesNotRenderTheWordNull() {
        render(
            "Fact.<ref>{{cite news |last=Müller |first=Jan " +
                    "|trans-title=A new determination of molecular dimensions " +
                    "|work=Annalen}}</ref>"
        )
        val list = render("{{reflist}}")

        assertTrue(list.contains("A new determination of molecular dimensions"))
        // `title += …` on a null title used to concatenate the string "null" into the citation.
        assertFalse("citation rendered the literal word null: $list", list.contains("null"))
    }

    @Test
    fun citationWithBothTitles_keepsBoth() {
        render(
            "Fact.<ref>{{cite news |last=Müller |title=Eine neue Bestimmung " +
                    "|trans-title=A new determination |work=Annalen}}</ref>"
        )
        val list = render("{{reflist}}")

        assertTrue(list.contains("Eine neue Bestimmung"))
        assertTrue(list.contains("A new determination"))
        assertFalse(list.contains("null"))
    }

    @Test
    fun citationWithEdition_rendersIt() {
        render("Fact.<ref>{{cite book |last=Knuth |title=TAOCP |edition=3rd}}</ref>")
        val list = render("{{reflist}}")

        assertTrue(list.contains("TAOCP"))
        assertTrue(list.contains("3rd ed."))
    }
}
