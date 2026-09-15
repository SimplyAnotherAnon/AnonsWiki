package org.nsh07.wikireader.ui.homeScreen

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCaptionTest {

    @Test
    fun caption_withWikiLink_isNotTruncatedAtThePipe() {
        assertEquals(
            "Einstein with [[Charlie Chaplin]] at the [[Hollywood, Los Angeles|Hollywood]] premiere",
            imageCaption(
                "[[File:Einstein_chaplin.jpg|Einstein with [[Charlie Chaplin]] at the " +
                        "[[Hollywood, Los Angeles|Hollywood]] premiere"
            )
        )
    }

    @Test
    fun caption_withTemplate_isKeptWhole() {
        assertEquals(
            "Einstein with colleagues, 1913{{efn|l-r: 1. [[Karl Herzfeld]]; 2. [[Otto Stern]]}}",
            imageCaption(
                "[[File:Zurich.jpg|Einstein with colleagues, 1913{{efn|l-r: 1. " +
                        "[[Karl Herzfeld]]; 2. [[Otto Stern]]}}"
            )
        )
    }

    @Test
    fun invertMarker_isNotPartOfTheCaption() {
        assertEquals(
            "Pathways of [[paracetamol]] metabolism",
            imageCaption("[[File:Paracetamol.svg|Pathways of [[paracetamol]] metabolism|invert")
        )
    }

    @Test
    fun imageWithoutCaption_givesEmptyString() {
        assertEquals("", imageCaption("[[File:Aspirin-skeletal.svg"))
        assertEquals("", imageCaption("[[File:Aspirin-skeletal.svg||invert"))
        // The infobox passes a bare file name.
        assertEquals("", imageCaption("Aspirin-skeletal.svg"))
    }
}
