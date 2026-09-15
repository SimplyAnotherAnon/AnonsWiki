package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The parser keeps its reference state in the global [ReferenceData], so it has to be cleared
 * between articles.
 */
class ReferenceStateTest {

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
    fun reset_clearsTheReferenceListBackingReflist() {
        // A long article: its later references sit at indices a shorter article never reaches.
        render("A.<ref>First source</ref>B.<ref>Second source</ref>C.<ref>Third source</ref>")
        assertTrue(render("{{reflist}}").contains("Third source"))

        ReferenceData.reset()

        // A short article. Numbering restarts, so it only overwrites index 1; without clearing
        // refListIndex the entries at 2 and 3 survive and show up in this article's references.
        render("Only fact.<ref>Only source</ref>")
        val second = render("{{reflist}}")

        assertTrue(second.contains("Only source"))
        assertFalse(second.contains("Second source"))
        assertFalse(second.contains("Third source"))
    }

    @Test
    fun reset_restartsReferenceNumbering() {
        render("A.<ref>One</ref>B.<ref>Two</ref>C.<ref>Three</ref>")
        ReferenceData.reset()

        render("Only fact.<ref>Only source</ref>")
        val list = render("{{reflist}}")

        assertTrue(list.trim().startsWith("1."))
        assertFalse(list.contains("Two"))
        assertFalse(list.contains("Three"))
    }
}
