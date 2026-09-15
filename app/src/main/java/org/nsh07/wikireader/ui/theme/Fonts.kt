package org.nsh07.wikireader.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** Values persisted for the body and heading font preferences. */
const val FONT_STYLE_SANS = "sans"
const val FONT_STYLE_SERIF = "serif"
const val FONT_STYLE_GOOGLE_SANS_FLEX = "gsf"

private const val GOOGLE_SANS_FLEX_NAME = "google-sans-flex"

private val googleSansFlex = FontFamily(Font(DeviceFontFamilyName(GOOGLE_SANS_FLEX_NAME)))

/**
 * Whether this device ships Google Sans Flex.
 *
 * [Typeface.create] falls back to the default typeface for a family it does not know, so a
 * device without the font returns the very same instance the default lookup does.
 */
val googleSansFlexAvailable: Boolean by lazy {
    runCatching {
        Typeface.create(GOOGLE_SANS_FLEX_NAME, Typeface.NORMAL) !=
                Typeface.create(null as Typeface?, Typeface.NORMAL)
    }.getOrDefault(false)
}

/**
 * Resolves a stored font preference to a [FontFamily]. Google Sans Flex degrades to sans-serif on
 * devices that do not have it, so a preference carried over from another device still renders.
 */
fun fontFamilyOf(fontStyle: String): FontFamily = when (fontStyle) {
    FONT_STYLE_SERIF -> FontFamily.Serif
    FONT_STYLE_GOOGLE_SANS_FLEX ->
        if (googleSansFlexAvailable) googleSansFlex else FontFamily.SansSerif

    else -> FontFamily.SansSerif
}
