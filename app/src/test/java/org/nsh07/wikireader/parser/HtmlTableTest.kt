package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Templates often emit HTML tables rather than wikitables. PsychonautWiki's substance box — the
 * routes of administration, dosages, durations and interactions — is one, nested tables and all.
 */
class HtmlTableTest {

    private fun rows(wikitext: String) = runBlocking {
        val table = cleanUpWikitext(wikitext)
        parseWikitable(table.trim(), lightColorScheme(), Typography(), {}, {}, 16)
            .second.map { row -> row.map { it.text.trim() } }
    }

    @Test
    fun aSimpleHtmlTable_becomesAWikitable() {
        val converted = convertHtmlTables(
            "<table><tr><th>Name</th><td>LSD</td></tr></table>"
        )
        assertTrue(converted.contains("{| class=\"wikitable\""))
        assertTrue(converted.contains("! Name"))
        assertTrue(converted.contains("| LSD"))
        assertTrue(converted.contains("|}"))
        assertFalse(converted.contains("<table"))
    }

    @Test
    fun headerAndCellsBecomeRows() {
        val parsed = rows(
            "<table><tr><th>Common names</th><td>LSD, Acid</td></tr>" +
                    "<tr><th>Chemical class</th><td>Lysergamide</td></tr></table>"
        )
        assertEquals(listOf("Common names", "LSD, Acid"), parsed[0])
        assertEquals(listOf("Chemical class", "Lysergamide"), parsed[1])
    }

    @Test
    fun colspanIsKept() {
        assertTrue(
            convertHtmlTables("<table><tr><th colspan=\"2\">LSD</th></tr></table>")
                .contains("! colspan=2 | LSD")
        )
    }

    @Test
    fun aNestedTableIsFlattenedIntoItsParent() {
        // A wikitable inside a cell would render as its own markup, because a cell is laid out as
        // text — so the rows of the routes-of-administration table join the substance box.
        val parsed = rows(
            "<table><tr><th>Routes</th></tr>" +
                    "<tr><td><table><tr><th>Threshold</th><td>15 µg</td></tr>" +
                    "<tr><th>Common</th><td>75 - 150 µg</td></tr></table></td></tr></table>"
        )
        val flat = parsed.joinToString(" ") { it.joinToString(" ") }
        assertFalse("nested markup leaked: $flat", flat.contains("{|"))
        assertTrue(flat.contains("Threshold"))
        assertTrue(flat.contains("15 µg"))
        assertTrue(flat.contains("75 - 150 µg"))
    }

    @Test
    fun interactionSeverityBecomesALabel() {
        // The severity is carried only by the cell's CSS class, so a text table would lose it.
        val parsed = rows(
            "<table>" +
                    "<tr><th class=\"SBInteractionDangerous\">Lithium</th></tr>" +
                    "<tr><th class=\"SBInteractionUnsafe\">Tramadol</th></tr>" +
                    "<tr><th class=\"SBInteractionUncertain\">Cannabis</th></tr>" +
                    "</table>"
        )
        val flat = parsed.joinToString(" ") { it.joinToString(" ") }
        assertTrue(flat.contains("Dangerous: Lithium"))
        assertTrue(flat.contains("Unsafe: Tramadol"))
        assertTrue(flat.contains("Caution: Cannabis"))
    }

    @Test
    fun cellContentSpanningLinesIsFlattened() {
        // The wikitable parser reads a table line by line.
        assertEquals(
            listOf(listOf("Systematic name", "a long chemical name")),
            rows("<table><tr><th>Systematic name</th><td>a long\n   chemical\n   name</td></tr></table>")
        )
    }

    @Test
    fun textAroundATableIsUntouched() {
        val converted = convertHtmlTables("before <table><tr><td>x</td></tr></table> after")
        assertTrue(converted.startsWith("before "))
        assertTrue(converted.trimEnd().endsWith("after"))
    }

    @Test
    fun anUnclosedTableIsLeftAlone() {
        val input = "text <table><tr><td>x</td></tr>"
        assertEquals(input, convertHtmlTables(input))
    }

    @Test
    fun wikitextWithoutHtmlTablesIsUnchanged() {
        val input = "{| class=\"wikitable\"\n|-\n! A\n| B\n|}"
        assertEquals(input, convertHtmlTables(input))
    }
}
