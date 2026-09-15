package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListItemTest {

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
    fun listItemAt_stopsAtTheFirstLineBreakOutsideMarkup() {
        val text = "* one\n* two\n"
        assertEquals("* one", text.listItemAt(0))
        assertEquals("* two", text.listItemAt(6))
    }

    @Test
    fun listItemAt_keepsAMultiLineTemplate() {
        val text = "* {{Cite journal\n| last = Einstein\n| year = 1901\n}}\n* next\n"
        assertEquals(
            "* {{Cite journal\n| last = Einstein\n| year = 1901\n}}",
            text.listItemAt(0)
        )
    }

    @Test
    fun listItemAt_keepsAMultiLineLink() {
        val text = "* see [[Some page|\nthe label]] here\nnext line\n"
        assertEquals("* see [[Some page|\nthe label]] here", text.listItemAt(0))
    }

    @Test
    fun bibliographyEntry_rendersAsACitationNotRawWikitext() {
        // Wikipedia writes bibliographies as one bullet holding a citation spread over many
        // lines; the parser used to cut at the first newline and spill the parameters.
        val out = render(
            "* {{Cite journal\n" +
                    "| last = Einstein\n" +
                    "| first = Albert\n" +
                    "| year = 1901\n" +
                    "| title = Folgerungen aus den Capillaritätserscheinungen\n" +
                    "| journal = [[Annalen der Physik]]\n" +
                    "| pages = 513–523\n" +
                    "}}\n"
        )

        assertFalse("raw parameters leaked: $out", out.contains("| last ="))
        assertFalse("raw markup leaked: $out", out.contains("}}"))
        assertTrue(out.contains("Einstein, Albert"))
        assertTrue(out.contains("1901"))
        assertTrue(out.contains("Folgerungen aus den Capillaritätserscheinungen"))
        assertTrue(out.contains("Annalen der Physik"))
    }

    @Test
    fun numberedListItem_alsoKeepsAMultiLineTemplate() {
        val out = render("# {{Cite book\n| last = Knuth\n| title = TAOCP\n}}\n")

        assertFalse("raw parameters leaked: $out", out.contains("| last ="))
        assertTrue(out.contains("Knuth"))
        assertTrue(out.contains("TAOCP"))
    }

    @Test
    fun ordinaryNestedBulletsStillRender() {
        val out = render("* one\n** nested\n* two\n")
        assertTrue(out.contains("one"))
        assertTrue(out.contains("nested"))
        assertTrue(out.contains("two"))
        assertFalse(out.contains("*"))
    }
}
