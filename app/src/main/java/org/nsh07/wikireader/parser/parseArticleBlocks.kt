package org.nsh07.wikireader.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.parser.ReferenceData.infoboxTemplates
import kotlin.math.min

/**
 * Splits a section of wikitext into the pieces the article screen renders separately.
 *
 * Prose is converted to an [AnnotatedString] here, while display math, galleries, images, tables
 * and infoboxes are emitted as their raw wikitext for the composables that know how to draw them
 * ([org.nsh07.wikireader.ui.homeScreen.ParsedBodyText] dispatches on the leading characters).
 */
suspend fun parseArticleBlocks(
    wikitext: String,
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    fontSize: Int,
    showRef: (String) -> Unit
): List<AnnotatedString> =
    withContext(Dispatchers.IO) {
        var curr = ""
        var i = 0
        var stack = 0
        val out = mutableListOf<AnnotatedString>()

        while (i < wikitext.length) {
            if (wikitext[i] == '{')
                stack++
            else if (wikitext[i] == '}')
                stack--

            if (wikitext[i] == '<') {
                var currSubstring = wikitext.substring(i, min(i + 20, wikitext.length))
                // Check for display math (case-insensitive)
                if (currSubstring.lowercase().startsWith("<math") && 
                    currSubstring.substringBefore('>').lowercase().contains("display")) {
                    currSubstring = wikitext.substring(i)
                    val closeIndex = currSubstring.lowercase().indexOf("</math>")
                    if (closeIndex != -1) {
                        currSubstring = wikitext.substring(i, i + closeIndex + 7)
                        out.add(
                            curr.toWikitextAnnotatedString(
                                colorScheme = colorScheme,
                                typography = typography,
                                loadPage = loadPage,
                                fontSize = fontSize,
                                showRef = {

                                }
                            )
                        )
                        out.add(AnnotatedString(currSubstring))
                        i += currSubstring.length
                        curr = ""
                    } else {
                        curr += wikitext[i]
                    }
                } else if (currSubstring.startsWith("<gallery")) {
                    currSubstring = wikitext.substring(i).substringBefore("</gallery>")
                    out.add(
                        curr.toWikitextAnnotatedString(
                            colorScheme = colorScheme,
                            typography = typography,
                            loadPage = loadPage,
                            fontSize = fontSize,
                            showRef = showRef
                        )
                    )
                    out.add(AnnotatedString(currSubstring))
                    i += currSubstring.length + 10
                    curr = ""
                } else curr += wikitext[i]
            } else if (stack == 0 && wikitext[i] == '[' && wikitext.getOrNull(i + 1) == '[') {
                val currSubstring = wikitext.substringMatchingParen('[', ']', i)
                if (currSubstring.contains(':')) {
                    if (currSubstring
                            .matches(
                                ".*\\.jpg.*|.*\\.jpeg.*|.*\\.png.*|.*\\.svg.*|.*\\.gif.*"
                                    .toRegex(RegexOption.IGNORE_CASE)
                            )
                    ) {
                        out.add(
                            curr.toWikitextAnnotatedString(
                                colorScheme = colorScheme,
                                typography = typography,
                                loadPage = loadPage,
                                fontSize = fontSize,
                                showRef = showRef
                            )
                        )
                        out.add(
                            buildAnnotatedString {
                                append("[[File:")
                                append(currSubstring.substringAfter(':').substringBefore('|'))
                                append('|')
                                append(
                                    currSubstring.substringAfter('|').substringBeforeLast("]]")
                                        .split('|')
                                        .filterNot { it.matches("thumb|thumbnail|frame|frameless|border|baseline|class=.*|center|left|right|upright.*|.+px|alt=.*".toRegex()) }
                                        .joinToString("|")
                                )
                                if (currSubstring.contains("class=skin-invert-image")) {
                                    append("|invert")
                                }
                            }
                        )
                        curr = ""
                        i += currSubstring.length - 1
                    } else
                        curr += wikitext[i]
                } else {
                    curr += wikitext[i]
                }
            } else if (wikitext[i] == '{') {
                if (wikitext.getOrNull(i + 1) == '|') {
                    // The whole table, nested ones included: substringMatchingParen already
                    // balances the braces. This used to hand back only the *inner* table of a
                    // nested pair and advance past the outer one by the inner one's length, so an
                    // infobox-style table built from nested tables — PsychonautWiki's substance
                    // box, with its routes of administration, dosages and durations — disappeared
                    // and left the parser reading from the middle of it.
                    val currSubstring = wikitext.substringMatchingParen('{', '}', i)
                    out.add(
                        curr.toWikitextAnnotatedString(
                            colorScheme = colorScheme,
                            typography = typography,
                            loadPage = loadPage,
                            fontSize = fontSize,
                            showRef = showRef
                        )
                    )
                    out.add(AnnotatedString(currSubstring))
                    curr = ""
                    i += currSubstring.length
                } else if (
                    stack < 2 && wikitext.getOrNull(i + 1) == '{' &&
                    wikitext.substring(i, min(i + 24, wikitext.length))
                        .let { subStr ->
                            infoboxTemplates.fastAny {
                                subStr.startsWith(it, true)
                            }
                        }
                ) {
                    val currSubstring = wikitext.substringMatchingParen('{', '}', i)
                    out.add(
                        curr.toWikitextAnnotatedString(
                            colorScheme = colorScheme,
                            typography = typography,
                            loadPage = loadPage,
                            fontSize = fontSize,
                            showRef = showRef
                        )
                    )
                    out.add(AnnotatedString(currSubstring))
                    curr = ""
                    i += currSubstring.length - 1
                } else curr += wikitext[i]
            } else curr += wikitext[i]
            i++
        }
        out.add(
            curr.toWikitextAnnotatedString(
                colorScheme = colorScheme,
                typography = typography,
                loadPage = loadPage,
                fontSize = fontSize,
                showRef = showRef
            )
        )
        out.toList()
    }
