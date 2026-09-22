package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndentAndFractionTest {

    private fun render(wikitext: String) = wikitext.toWikitextAnnotatedString(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        loadPage = {},
        fontSize = 16,
        showRef = {}
    ).text

    @Test
    fun colonIndentedLine_isIndentedNotPrefixedWithColons() {
        // Wikipedia indents equations and definitions with leading colons.
        val out = render("with reaction enthalpy 178 kJ/mol:\n::{{chem2|CaCO3(s) -> CaO(s)}}\n")

        assertFalse("indent markers shown: $out", out.contains("::"))
        assertTrue(out.contains("CaCO3(s) → CaO(s)"))
    }

    @Test
    fun colonInsideALineIsLeftAlone() {
        assertEquals("see https://example.org for more", render("see https://example.org for more"))
        assertEquals("Note: this is fine", render("Note: this is fine"))
    }

    @Test
    fun fractionWithATemplateDenominator_isNotCutAtItsPipe() {
        val out = render("{{sfrac|''K''<sub>sp</sub>|[{{chem2|CO3(2−)}}]}}")

        assertFalse("markup leaked: $out", out.contains("}}"))
        assertTrue(out.contains("Ksp"))
        assertTrue(out.contains("CO3"))
    }

    @Test
    fun ordinaryFractionsStillRender() {
        assertEquals("1/2", render("{{sfrac|2}}"))
        assertEquals("3/4", render("{{sfrac|3|4}}"))
    }
}
