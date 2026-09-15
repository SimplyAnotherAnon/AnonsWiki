package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Citation templates the specific handlers do not cover used to render as their own raw wikitext.
 * The examples are taken from the reference lists of real articles.
 */
class CitationFallbackTest {

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
    fun citeMagazine_rendersAsACitation() {
        val out = render(
            "{{cite magazine |last=Lizza |first=Ryan |date=March 19, 2007 " +
                    "|title=The Agitator |magazine=The New Republic}}"
        )

        assertFalse("raw wikitext leaked: $out", out.contains("{{"))
        assertTrue(out.contains("Lizza, Ryan"))
        assertTrue(out.contains("March 19, 2007"))
        assertTrue(out.contains("The Agitator"))
        assertTrue(out.contains("The New Republic"))
    }

    @Test
    fun citeEncyclopedia_thesis_speech_court_allRender() {
        listOf(
            "{{cite encyclopedia |vauthors=Ling G |title=Aspirin |encyclopedia=Martindale}}",
            "{{cite thesis |last=Einstein |first=Albert |date=1905 |title=Molecular dimensions}}",
            "{{cite speech |last=Einstein |first=Albert |year=1923 |title=Nobel lecture}}",
            "{{cite court |litigants=Bayer Co. v. United Drug Co. |year=1921}}",
            "{{citation |last=Knuth |title=TAOCP |publisher=Addison-Wesley |isbn=0201896834}}",
        ).forEach { template ->
            val out = render(template)
            assertFalse("raw wikitext leaked for $template: $out", out.contains("{{"))
            assertTrue("nothing rendered for $template", out.isNotBlank())
        }
    }

    @Test
    fun parametersContainingPipes_areNotCutInHalf() {
        val out = render(
            "{{cite magazine |last=Graff |title=[[What Makes Obama Run?|The profile]] " +
                    "|magazine=Washingtonian}}"
        )

        assertTrue(out.contains("The profile"))
        assertTrue(out.contains("Washingtonian"))
    }

    @Test
    fun anEmptyCitation_rendersNothingRatherThanMarkup() {
        assertEquals("", render("{{cite magazine}}"))
    }

    @Test
    fun identifiersAreLabelled() {
        val out = render("{{cite report |title=A report |doi=10.1000/xyz |isbn=0201896834}}")

        assertTrue(out.contains("doi:10.1000/xyz"))
        assertTrue(out.contains("ISBN 0201896834"))
    }
}
