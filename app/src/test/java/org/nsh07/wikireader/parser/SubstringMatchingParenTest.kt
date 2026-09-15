package org.nsh07.wikireader.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class SubstringMatchingParenTest {

    @Test
    fun balancedTemplate_returnsJustThatTemplate() {
        val text = "before {{template|arg}} after"
        assertEquals(
            "{{template|arg}}",
            text.substringMatchingParen('{', '}', text.indexOf("{{"))
        )
    }

    @Test
    fun nestedTemplate_matchesTheOuterBraces() {
        val text = "before {{outer|{{inner}}|x}} after"
        assertEquals(
            "{{outer|{{inner}}|x}}",
            text.substringMatchingParen('{', '}', text.indexOf("{{"))
        )
    }

    @Test
    fun unbalancedTemplate_returnsTheRestOfTheString_notTheWholeString() {
        // Unclosed braces are common in wikitext, and a section split can cut a template in half.
        // Returning the whole string made callers emit the text that came *before* the template
        // as part of it, and advance their cursor by more than they had consumed.
        val text = "some earlier prose {{unclosed|arg"
        assertEquals(
            "{{unclosed|arg",
            text.substringMatchingParen('{', '}', text.indexOf("{{"))
        )
    }

    @Test
    fun unbalancedFromStart_isUnchanged() {
        val text = "{{unclosed|arg"
        assertEquals(text, text.substringMatchingParen('{', '}', 0))
    }
}
