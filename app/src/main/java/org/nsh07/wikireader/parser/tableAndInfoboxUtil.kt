package org.nsh07.wikireader.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.text.Typography.mdash

/**
 * Converts a Wikitable into a format which is possible to be displayed by the app
 *
 * @param table The Wikitable to be parsed
 * @param colorScheme [ColorScheme] composition local
 * @param typography [Typography] composition local
 * @param loadPage Function to load a page, to be provided by the [androidx.lifecycle.ViewModel]
 * @param showRef Function to show the reference bottom sheet/popup with the given reference string
 * @param fontSize Font size in app preferences
 *
 * @return A [Pair] containing the table caption at the first value, and the table matrix as the
 * second value (both are [AnnotatedString]s)
 */
suspend fun parseWikitable(
    table: String,
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    showRef: (String) -> Unit,
    fontSize: Int
): Pair<AnnotatedString, List<List<AnnotatedString>>> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<MutableList<AnnotatedString>>()
    var caption = AnnotatedString("")
    val lines = table.lines().fastMap { it.trim() }.fastFilter { it.isNotEmpty() }
    val styling =
        "class=|align=|scope=|style=|rowspan=|colspan=|width=|[^{]nowrap|data-sort".toRegex()

    var currentRow = mutableListOf<AnnotatedString>()
    val rowSpan = mutableMapOf<Int, Int>()
    var maxSize = 0
    var lastNestedTableEnd = 2
    var insideTable = false

    for (line in lines) {
        var currColumnIndex = currentRow.size

        while (true) {
            val remaining = rowSpan[currColumnIndex] ?: break
            if (remaining <= 1) break
            currentRow.add(AnnotatedString(""))
            if (remaining - 1 <= 1) rowSpan.remove(currColumnIndex)
            else rowSpan[currColumnIndex] = remaining - 1
            currColumnIndex++
        }

        when {
            line.startsWith("{|") -> {
                // Table begin
                if (!insideTable) insideTable = true
                else {
                    currentRow[currentRow.lastIndex] += AnnotatedString(
                        table.substringMatchingParen(
                            '{',
                            '}',
                            table.indexOf("{|", lastNestedTableEnd)
                        )
                    )
                    lastNestedTableEnd = table.indexOf("|}", lastNestedTableEnd)
                }
            }

            line.startsWith("|+") -> {
                // Table caption
                caption = "'''${line.removePrefix("|+").trim()}'''".toWikitextAnnotatedString(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                )
            }

            line.startsWith("|}") -> {
                // Table end
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                insideTable = false
            }

            line.startsWith("|-") -> {
                // Row delimiter
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    maxSize = max(currentRow.size, maxSize) // Keep track of max row size
                    currentRow = mutableListOf()
                }
            }

            line.startsWith("!") -> {
                // Header cells, can be split by '!!' or on multiple lines
                val content = line.removePrefix("!")
                val cells = mutableListOf<AnnotatedString>()
                var sep = "!!"
                val pipeSep = "||" in line
                if ("!!" in line || pipeSep) {
                    if (pipeSep) sep = "||"
                    content.split(sep).fastForEach {
                        var colspan = 1
                        if (it.contains("colspan")) {
                            colspan = it.substringAfter("colspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                        }
                        val curr =
                            if (it.contains(styling)) {
                                it.substringAfter('|')
                            } else it

                        if (it.contains("rowspan")) { // Read rowspan count of cell
                            rowSpan[currentRow.size] =
                                it.substringAfter("rowspan=").substringBefore('|')
                                    .substringBefore(' ').trim('"')
                                    .toIntOrNull() ?: 1
                        }

                        cells.add(
                            ("'''${curr.trim()}'''")
                                .toWikitextAnnotatedString(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    showRef = showRef
                                )
                        )
                        repeat(colspan - 1) {
                            cells.add(AnnotatedString(""))
                        }
                    }
                } else {
                    var colspan = 1
                    if (content.contains("colspan")) {
                        colspan = content.substringAfter("colspan=").substringBefore('|')
                            .substringBefore(' ').trim('"')
                            .toIntOrNull() ?: 1
                    }
                    val curr =
                        if (content.contains(styling)) {
                            content.substringAfter('|')
                        } else content

                    if (content.contains("rowspan")) { // Store rowspan count of cell
                        rowSpan[currentRow.size] =
                            content.substringAfter("rowspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                    }

                    cells.add(
                        ("'''${curr.trim()}'''")
                            .toWikitextAnnotatedString(
                                colorScheme,
                                typography,
                                loadPage,
                                fontSize,
                                showRef = showRef
                            )
                    )
                    repeat(colspan - 1) {
                        cells.add(AnnotatedString(""))
                    }
                }
                currentRow.addAll(cells)
            }

            line.startsWith("|") -> {
                // Data cells, can be split by '||' or on multiple lines
                val content = line.removePrefix("|")
                val cells = mutableListOf<AnnotatedString>()
                if ("||" in content) {
                    content.split("||").fastForEach {
                        var colspan = 1
                        if (it.contains("colspan")) {
                            colspan = it.substringAfter("colspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                        }
                        val curr =
                            if (it.contains(styling)) {
                                it.substringAfter('|')
                            } else it

                        if (it.contains("rowspan")) { // Read rowspan count of cell
                            rowSpan[currentRow.size] =
                                it.substringAfter("rowspan=").substringBefore('|')
                                    .substringBefore(' ').trim('"')
                                    .toIntOrNull() ?: 1
                        }

                        if (it.contains("{{n/a}}", ignoreCase = true)) {
                            cells.add(AnnotatedString("$mdash"))
                        } else {
                            cells.add(
                                curr.trim()
                                    .toWikitextAnnotatedString(
                                        colorScheme,
                                        typography,
                                        loadPage,
                                        fontSize,
                                        showRef = showRef
                                    )
                            )
                        }
                        repeat(colspan - 1) {
                            cells.add(AnnotatedString(""))
                        }
                    }
                } else {
                    var colspan = 1
                    if (content.contains("colspan")) {
                        colspan = content.substringAfter("colspan=").substringBefore('|')
                            .substringBefore(' ').trim('"')
                            .toIntOrNull() ?: 1
                    }
                    val curr =
                        if (content.contains(styling)) {
                            content.substringAfter('|')
                        } else content

                    if (content.contains("rowspan")) { // Read rowspan count of cell
                        rowSpan[currentRow.size] =
                            content.substringAfter("rowspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                    }

                    if (content.contains("{{n/a}}", ignoreCase = true)) {
                        cells.add(AnnotatedString("$mdash"))
                    } else {
                        cells.add(
                            curr.trim()
                                .toWikitextAnnotatedString(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    showRef = showRef
                                )
                        )
                    }
                    repeat(colspan - 1) {
                        cells.add(AnnotatedString(""))
                    }
                }
                currentRow.addAll(cells)
            }

            else -> {
                // Possibly a continuation of a multi-line cell?
                if (insideTable) {
                    val lastIndex = currentRow.lastIndex
                    if (lastIndex != -1)
                        currentRow[lastIndex] += line.trim()
                            .toWikitextAnnotatedString(
                                colorScheme,
                                typography,
                                loadPage,
                                fontSize,
                                showRef = showRef
                            )
                }
            }
        }
    }

    if (currentRow.isNotEmpty()) {
        rows.add(currentRow)
    }

    for (i in 0 until rows.size) {
        // Sanity check to ensure all rows are the same size
        if (rows[i].size < maxSize) {
            repeat(maxSize - rows[i].size) {
                rows[i].add(AnnotatedString(""))
            }
        }
    }

    Pair(caption, rows)
}

/**
 * Converts an Infobox into a format which is possible to be displayed by the app
 *
 * @param infoboxSource The Infobox to be parsed
 * @param colorScheme [ColorScheme] composition local
 * @param typography [Typography] composition local
 * @param loadPage Function to load a page, to be provided by the [androidx.lifecycle.ViewModel]
 * @param showRef Function to show the reference bottom sheet/popup with the given reference string
 * @param fontSize Font size in app preferences
 *
 * @return a [List] of [Pair]s of the Infobox entries, in key-value format.
 */
suspend fun parseInfobox(
    infoboxSource: String,
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    showRef: (String) -> Unit,
    fontSize: Int
): List<Pair<AnnotatedString, AnnotatedString>> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<Pair<AnnotatedString, AnnotatedString>>()
    val locale = Locale.getDefault()

    val lines = infoboxSource.lines().fastFilter { it.isNotEmpty() }.drop(1)
    var currentRowKey = ""
    var currentRowVal = ""
    var templateDepth = 0

    lines.fastForEach { item ->
        val it = item.trim()
        if (templateDepth == 0 && it.startsWith('|') && it.contains('=')) {
            if (currentRowVal.matches(".{1,6}:.+".toRegex())) { // Add image data in plaintext
                rows.add(Pair(AnnotatedString(currentRowKey), AnnotatedString(currentRowVal)))
            } else if (currentRowVal.isNotBlank()) {
                rows.add(
                    Pair(
                        currentRowKey.toWikitextAnnotatedString(
                            colorScheme,
                            typography,
                            loadPage,
                            fontSize,
                            showRef = showRef
                        ),
                        currentRowVal.toWikitextAnnotatedString(
                            colorScheme,
                            typography,
                            loadPage,
                            fontSize,
                            showRef = showRef
                        )
                    )
                )
            }
            currentRowKey = ""
            currentRowVal = ""

            val thisRow = it.split('=', limit = 2)
            currentRowKey = thisRow[0]
                .trim(' ', '|')
                .replace('_', ' ')
                .replace("currentowner", "current owner", ignoreCase = true)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            currentRowVal = thisRow[1].trim()
        } else if (templateDepth > 0 || it != "}}") {
            currentRowVal += '\n' + it
        }
        templateDepth = (templateDepth + it.windowed(2).count { braces -> braces == "{{" }
                - it.windowed(2).count { braces -> braces == "}}" }).coerceAtLeast(0)
    }

    if (currentRowVal.isNotBlank()) {
        rows.add(
            Pair(
                currentRowKey.toWikitextAnnotatedString(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                ),
                currentRowVal.trim('\n', ' ').toWikitextAnnotatedString(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                )
            )
        )
    }

    rows.fastFilter { (key, value) -> isReaderVisibleInfoboxRow(key.text, value.text) }
}

/**
 * Chrome parameters: an image, a map, a flag, a signature and everything that positions or styles
 * one. MediaWiki turns these into the picture at the top of the infobox, never into a labelled row.
 */
private val chromeParameter =
    ("^(image|img|photo|picture|cover|logo|seal|flag|emblem|blank ?emblem|map|mapframe|signature" +
            "|caption|alt|border|upright)").toRegex(RegexOption.IGNORE_CASE)

/** Presentational suffixes, e.g. "Flag size", "Map caption", "Infobox width". */
private val presentationSuffix =
    ("(size|width|upright|align|alignment|style|colou?r|background|padding|border" +
            "|px|pixels)$").toRegex(RegexOption.IGNORE_CASE)

/**
 * Bookkeeping parameters used by the chemistry and drug infoboxes to track which fields a bot has
 * checked. They are invisible on Wikipedia.
 */
private val bookkeepingParameter =
    ("^(verified ?fields|watched ?fields|verified ?revid|revid|embedded|child|module|nocat|noicon" +
            "|index ?label|sortkey|template ?doc|demo)$").toRegex(RegexOption.IGNORE_CASE)

/**
 * Slots that only carry a footnote or an aside for a neighbouring field, e.g. "Legal US comment"
 * next to "Legal US". On their own they render as a stray marker.
 */
private val footnoteSlot = "(ref|refs|comment|comments)$".toRegex(RegexOption.IGNORE_CASE)

/** Values that switch a section of the template on or off rather than saying anything. */
private val switchValue = "^(yes|no|y|n|on|off|true|false)$".toRegex(RegexOption.IGNORE_CASE)

/**
 * A value naming an image file. [org.nsh07.wikireader.ui.homeScreen.AsyncInfobox] renders these as
 * pictures, so such a row is content however plumbing-like its parameter name looks — the album
 * cover lives in `Cover`, the structural diagram in `ImageL`, the autograph in `Signature`.
 */
private val mediaValue =
    "\\.(jpg|jpeg|png|svg|gif|webp)$".toRegex(RegexOption.IGNORE_CASE)

/**
 * Decides whether an infobox row is something Wikipedia would show a reader.
 *
 * [parseInfobox] reads every `|name = value` pair in the template, but a MediaWiki infobox only
 * renders the parameters it has a label for; the rest drive images, layout and bot bookkeeping.
 * Without the template definitions we cannot know the labelled set, so this drops the parameter
 * shapes that are plumbing in every infobox family, and keeps everything else.
 */
internal fun isReaderVisibleInfoboxRow(key: String, value: String): Boolean {
    // Trailing indices distinguish repeats ("Image2", "Alt2"), not different kinds of parameter.
    val name = key.trim().trimEnd(*"0123456789".toCharArray()).trim()
    if (name.isEmpty()) return false

    val text = value.trim()
    if (mediaValue.containsMatchIn(text) || text.matches("\\[\\[.{1,6}:.+]]".toRegex())) return true

    if (chromeParameter.containsMatchIn(name)) return false
    if (presentationSuffix.containsMatchIn(name)) return false
    if (bookkeepingParameter.containsMatchIn(name)) return false
    if (footnoteSlot.containsMatchIn(name)) return false

    if (text.isEmpty()) return false
    if (switchValue.matches(text)) return false
    // Punctuation-only leftovers such as the ", " an empty template parameter list leaves behind.
    if (text.none { it.isLetterOrDigit() }) return false

    return true
}