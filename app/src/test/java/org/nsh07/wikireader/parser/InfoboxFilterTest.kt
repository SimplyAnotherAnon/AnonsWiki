package org.nsh07.wikireader.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks which infobox parameters reach the reader. The cases here are taken from the rendered
 * infoboxes of real articles (Aspirin, Caffeine, Barack Obama, Tokyo, France, Earth, Thriller,
 * Cristiano Ronaldo, Albert Einstein).
 */
class InfoboxFilterTest {

    private fun assertHidden(key: String, value: String = "something") =
        assertFalse("$key should not reach the reader", isReaderVisibleInfoboxRow(key, value))

    private fun assertShown(key: String, value: String = "something") =
        assertTrue("$key should reach the reader", isReaderVisibleInfoboxRow(key, value))

    @Test
    fun bookkeepingParameters_areHidden() {
        // Chemistry and drug infoboxes use these to record bot verification. Wikipedia never
        // shows them; before this filter they rendered as ordinary rows.
        assertHidden("Verifiedfields", "changed")
        assertHidden("Watchedfields", "changed")
        assertHidden("Verifiedrevid", "408971931")
    }

    @Test
    fun imageAndLayoutPlumbing_isHidden() {
        assertHidden("Image class", "skin-invert-image")
        assertHidden("Image classL", "skin-invert-image")
        assertHidden("Image classR", "bg-transparent")
        assertHidden("Image size", "200")
        assertHidden("Image upright", "1.2")
        assertHidden("Image alt", "A white powder")
        assertHidden("Image caption", "Pure acetylsalicylic acid")
        assertHidden("Alt2", "Ball-and-stick model")
        assertHidden("Caption2", "Pure acetylsalicylic acid")
        assertHidden("Flag size", "110")
        assertHidden("Flag link", "Symbols of Tokyo#As a flag")
        assertHidden("Seal type", "Symbol")
        assertHidden("Logo size", "200")
        assertHidden("Map caption", "Location of Tokyo")
        assertHidden("Width", "187")
        assertHidden("Mapframe-zoom", "9")
    }

    @Test
    fun footnoteOnlySlots_areHidden() {
        // These carry a reference for a neighbouring field and render as a stray marker alone.
        assertHidden("Legal US comment", "/ Rx-only")
        assertHidden("Pregnancy AU comment", "1")
        assertHidden("Elevation ref", "[1]")
        assertHidden("HDI ref", "[2]")
    }

    @Test
    fun templateSwitches_areHidden() {
        // Values that turn a block of the template on or off rather than saying anything.
        assertHidden("Atmosphere", "yes")
        assertHidden("Note", "no")
        assertHidden("Allsatellites", "yes")
        assertHidden("Border", "yes")
    }

    @Test
    fun emptyAndPunctuationOnlyValues_areHidden() {
        assertHidden("ATC supplemental", ", ")
        assertHidden("Something", "")
        assertHidden("Something", "   ")
    }

    @Test
    fun contentParameters_areShown() {
        // Regression guard: these read like plumbing but are what the article is about.
        assertShown("Class", "Nonsteroidal anti-inflammatory drug (NSAID)")
        assertShown("Height", "1.87 m")
        assertShown("Children", "2")
        assertShown("Tradename", "Bayer Aspirin, others")
        assertShown("Nationalteam1", "Portugal U15")
        assertShown("Melting point", "135")
    }

    @Test
    fun imageValues_areShownWhateverTheParameterIsCalled() {
        // AsyncInfobox renders these as pictures, so hiding the row would lose the album cover,
        // the structural diagram or the signature.
        assertShown("Cover", "Michael Jackson - Thriller.png")
        assertShown("ImageL", "Aspirin-skeletal.svg")
        assertShown("ImageR", "Aspirin-B-3D-balls.png")
        assertShown("Image2", "acetylsalicylic_acid.jpg")
        assertShown("Logo", "Python-logo-notext.svg")
        assertShown("Signature", "Barack Obama signature.svg")
        assertShown("Image flag", "Flag of France.svg")
        assertShown("Image seal", "PrefSymbol-Tokyo.svg")
    }

    @Test
    fun trailingIndices_doNotChangeTheDecision() {
        assertHidden("Image class2", "skin-invert-image")
        assertHidden("Map size1", "250")
        assertHidden("Width2", "187")
    }
}
