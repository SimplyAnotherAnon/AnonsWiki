package org.nsh07.wikireader.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.github.tomtung.latex2unicode.LaTeX2Unicode
import org.nsh07.wikireader.data.LanguageData.alpha3CodeToAlpha2CodeMap
import org.nsh07.wikireader.data.LanguageData.countryNameToAlpha2CodeMap
import org.nsh07.wikireader.data.LanguageData.fifaCodeToAlpha3CodeMap
import org.nsh07.wikireader.data.countryFlag
import org.nsh07.wikireader.data.formatToHumanReadable
import org.nsh07.wikireader.data.langCodeToName
import org.nsh07.wikireader.parser.ReferenceData.refCount
import org.nsh07.wikireader.parser.ReferenceData.refList
import org.nsh07.wikireader.parser.ReferenceData.refListCount
import org.nsh07.wikireader.parser.ReferenceData.refListIndex
import org.nsh07.wikireader.parser.ReferenceData.refTemplate
import org.nsh07.wikireader.parser.ReferenceData.refTemplates
import kotlin.math.min
import kotlin.text.Typography.bullet
import kotlin.text.Typography.mdash
import kotlin.text.Typography.nbsp
import kotlin.text.Typography.ndash

private const val MAGIC_SEP = "{{!}}"
private const val MAX_WIKITEXT_RECURSION_DEPTH = 64
private val NUMBERED_PARAMETER = "^\\s*\\d+\\s*=\\s*".toRegex()

private fun String.linkArgument(): String = replaceFirst(NUMBERED_PARAMETER, "").trim()

/** Replaces wiki links with the text they display, for contexts that render plain characters. */
private fun String.plainLinkText(): String =
    replace("\\[\\[([^\\[\\]|]*\\|)?([^\\[\\]|]*)]]".toRegex()) { it.groupValues[2] }.trim()

/**
 * Templates standing in for a character that would otherwise be read as markup. The key is the
 * template with its closing braces already stripped, as the parser sees it.
 */
private val escapeTemplates = mapOf(
    "{{!" to "|",
    "{{!!" to "||",
    "{{=" to "=",
    "{{(" to "{",
    "{{)" to "}",
    "{{'" to "’",
    "{{'-" to "’",
    "{{-'" to "’",
    "{{`" to "‘",
)

/** An HTML tag, so unknown ones can be dropped rather than printed. */
private val htmlTag = "</?[a-zA-Z][a-zA-Z0-9]*(\\s[^<>]*)?/?>".toRegex()

/**
 * True when this template's name is [name] — that is, the name is followed by a parameter
 * separator, a space or the end of the template rather than by more letters.
 *
 * Plain `startsWith` lets a short name swallow a longer one that is not handled: `{{for` matched
 * `{{formatnum`, so an inflation-adjusted price rendered as the hatnote text "For US, see".
 * Names that already end in a separator are matched as-is.
 */
private fun String.isTemplate(name: String): Boolean {
    if (!startsWith("{{$name", ignoreCase = true)) return false
    if (!name.last().isLetterOrDigit()) return true
    val next = getOrNull(name.length + 2) ?: return true
    return !next.isLetterOrDigit()
}

/** Matches an ion charge such as `2+`, `+`, `3-` or `2−`. */
private val CHEM_CHARGE = "^\\d*[+\u2212-]$".toRegex()

/**
 * Renders the body of a {{chem2}} template, the markup Wikipedia uses for inline chemical
 * formulae and equations (e.g. `CaCO3`, `Ca(2+)`, `CaCO3*6H2O`, `CaCO3(s) -> CaO(s) + CO2(g)`).
 *
 * Digits that directly follow an element symbol or a closing bracket become subscripts, charges
 * written in parentheses or trailing a symbol become superscripts, and the ASCII reaction arrows
 * are replaced with their typographic equivalents.
 */
private fun AnnotatedString.Builder.appendChem2(formula: String, fontSize: Int) {
    val subscript = SpanStyle(
        baselineShift = BaselineShift.Subscript,
        fontSize = (fontSize - 4).sp
    )
    val superscript = SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = (fontSize - 4).sp
    )

    var i = 0
    // Tracks the last character appended in normal script, so we can tell a subscript digit
    // ("H2O") from a stoichiometric coefficient ("2 H2O"), and a charge ("H+") from a plus
    // sign joining two species ("CaO + H2O").
    var prev = ' '

    fun attachesToPrevious() = prev.isLetterOrDigit() || prev == ')' || prev == ']'

    fun appendArrow(arrow: Char, source: String, at: Int, length: Int) {
        if (!prev.isWhitespace()) append(' ')
        append(arrow)
        if (source.getOrNull(at + length)?.isWhitespace() != true) append(' ')
    }

    while (i < formula.length) {
        val c = formula[i]
        when {
            formula.startsWith("<=>", i) || formula.startsWith("<->", i) -> {
                appendArrow('\u21cc', formula, i, 3); prev = ' '; i += 3
            }

            formula.startsWith("->", i) -> {
                appendArrow('\u2192', formula, i, 2); prev = ' '; i += 2
            }

            formula.startsWith("<-", i) -> {
                appendArrow('\u2190', formula, i, 2); prev = ' '; i += 2
            }

            c == '*' || c == '\u00b7' -> { // hydrate separator
                append('\u00b7'); prev = '\u00b7'; i++
            }

            c == '(' -> {
                val close = formula.indexOf(')', i)
                val inner = if (close == -1) null else formula.substring(i + 1, close)
                if (inner != null && inner.matches(CHEM_CHARGE)) {
                    withStyle(superscript) { append(inner.replace('-', '\u2212')) }
                    prev = ')'
                    i = close + 1
                } else {
                    append('('); prev = '('; i++
                }
            }

            c.isDigit() && attachesToPrevious() -> {
                val digits = formula.substring(i).takeWhile { it.isDigit() }
                withStyle(subscript) { append(digits) }
                // A subscript does not itself anchor a following charge-less digit run.
                prev = '0'
                i += digits.length
            }

            (c == '+' || c == '-' || c == '\u2212') && attachesToPrevious() &&
                    formula.getOrNull(i + 1)?.isLetterOrDigit() != true -> {
                withStyle(superscript) { append(if (c == '-') '\u2212' else c) }
                prev = c
                i++
            }

            else -> {
                append(c); prev = c; i++
            }
        }
    }
}

/**
 * Renders the parameters of the older {{chem}} template, where odd parameters are normal text and
 * even ones are subscripts, except for charges (`2-`, `+`) which are superscripts regardless of
 * position, e.g. `{{chem|SO|4|2-}}`.
 */
private fun AnnotatedString.Builder.appendChem(params: List<String>, fontSize: Int) {
    params.fastForEachIndexed { index, raw ->
        val param = raw.trim()
        if (param.isEmpty() || '=' in param) return@fastForEachIndexed
        when {
            param.matches(CHEM_CHARGE) -> withStyle(
                SpanStyle(
                    baselineShift = BaselineShift.Superscript,
                    fontSize = (fontSize - 4).sp
                )
            ) { append(param.replace('-', '\u2212')) }

            index % 2 == 1 -> withStyle(
                SpanStyle(
                    baselineShift = BaselineShift.Subscript,
                    fontSize = (fontSize - 4).sp
                )
            ) { append(param) }

            else -> append(param)
        }
    }
}

private val HATNOTE_LABEL =
    "^l(\\d+)\\s*=\\s*(.*)$".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val NAMED_PARAMETER =
    "^[a-zA-Z][\\w-]*\\s*=.*$".toRegex(RegexOption.DOT_MATCHES_ALL)
private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * Resolves hatnote template parameters into (target, label) links, supporting `lN=` display
 * labels (e.g. `{{main|Reacher season 1|l1=''Reacher'' season 1}}`).
 */
private fun hatnoteLinks(params: List<String>): List<Pair<String, String>> {
    val labels = mutableMapOf<Int, String>()
    val targets = mutableListOf<String>()

    params.forEach { param ->
        val label = HATNOTE_LABEL.find(param.trim())
        if (label != null) {
            labels[label.groupValues[1].toInt()] = label.groupValues[2].trim()
        } else {
            val target = param.linkArgument()
            if (target.isNotEmpty() && !target.matches(NAMED_PARAMETER)) targets.add(target)
        }
    }

    return targets.mapIndexed { index, target ->
        target.substringBefore(MAGIC_SEP) to
                (labels[index + 1] ?: target.substringAfter(MAGIC_SEP))
    }
}

private object WikitextParserState {
    val recursionDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }
}

/**
 * Converts Wikitext source code into an [AnnotatedString] that can be rendered by [androidx.compose.material3.Text]
 */
fun String.toWikitextAnnotatedString(
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    fontSize: Int,
    newLine: Boolean = true,
    inIndentCode: Boolean = false,
    showRef: (String) -> Unit,
): AnnotatedString {
    // ThreadLocal.get() is a platform type; the initial value means it is never actually null.
    val currentDepth = WikitextParserState.recursionDepth.get() ?: 0
    if (currentDepth >= MAX_WIKITEXT_RECURSION_DEPTH) {
        // Fallback: avoid runaway recursion on pathological or malformed wikitext
        return AnnotatedString(this)
    }

    WikitextParserState.recursionDepth.set(currentDepth + 1)
    try {
        val hrChar = '─'
        val input = this
        var i = 0
        var number = 1 // Count for numbered lists

        var italic = false
        var bold = false

        val twas: String.() -> AnnotatedString = {
            this.toWikitextAnnotatedString(
                colorScheme,
                typography,
                loadPage,
                fontSize,
                showRef = showRef
            )
        }

        val twasNoNewline: String.() -> AnnotatedString = {
            this.toWikitextAnnotatedString(
                colorScheme,
                typography,
                loadPage,
                fontSize,
                newLine = false,
                showRef = showRef
            )
        }

        return buildAnnotatedString {
            while (i < input.length) {
                if (input[i] != '#') number = 1
                when (input[i]) {
                ' ' ->
                    if ((getOrNull(i - 1) == '\n' || i == 0) && !inIndentCode) {
                        val curr = substring(i + 1).substringBefore('\n')
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(
                                curr.toWikitextAnnotatedString(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    inIndentCode = true,
                                    showRef = showRef
                                )
                            )
                        }
                        i += curr.length
                    } else append(input[i])

                '<' -> {
                    val currSubstring = input.substring(i)
                    when {
                        currSubstring.startsWith("<nowiki>") -> {
                            val curr =
                                currSubstring.substringBefore("</nowiki>").substringAfter('>')
                            append(curr)
                            i += 8 + curr.length + 8
                        }

                        currSubstring.startsWith("<ref", ignoreCase = true) -> {
                            // Handle references
                            // When a reference footnote is clicked, the showRef lambda is called
                            val open = currSubstring.substringMatchingParen('<', '>')
                            val refName = open
                                .substringAfter("name=")
                                .removeSuffix(">")
                                .trim(' ', '"', '/')
                            if (open.getOrNull(4) == '>') {
                                // Style 1: Plain reference without named references
                                val curr =
                                    currSubstring.substringBefore("</ref>").substringAfter('>')
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(curr)
                                    }
                                ) {
                                    append("<sup>$refCount </sup>".twas())
                                    refListIndex[refCount] = curr
                                    refCount++
                                }
                                i += 5 + curr.length + 5
                            } else if (open.endsWith("/>")) {
                                // Style 2: reference that refers to a named reference
                                val refWt = refList[refName] ?: ""
                                if (refListCount[refWt] == null) {
                                    refListCount[refWt] = refCount
                                    refListIndex[refCount] = refWt
                                    refCount++
                                }
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) { append("<sup>${refListCount[refWt]} </sup>".twas()) }
                                i += open.length - 1
                            } else {
                                // Style 3: Names reference definition
                                val refWt = refList[refName] ?: ""
                                if (refListCount[refWt] == null) {
                                    refListCount[refWt] = refCount
                                    refList[refName]
                                    refCount++
                                }
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) { append("<sup>${refListCount[refWt]} </sup>".twas()) }
                                i += currSubstring.substringBefore("</ref>").length + 5
                            }
                        }

                        currSubstring.startsWith("<br", ignoreCase = true) -> {
                            append('\n')
                            i += currSubstring.substringBefore('>').length
                        }

                        currSubstring.startsWith("<hr", ignoreCase = true) -> {
                            append('\n')
                            repeat(5) { append(hrChar) }
                            append('\n')
                            i += currSubstring.substringBefore('>').length
                        }

                        currSubstring.startsWith("<u>") -> {
                            val curr = currSubstring.substringBefore("</u>").substringAfter('>')
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(curr.twasNoNewline())
                            }
                            i += 3 + curr.length + 3
                        }

                        currSubstring.startsWith("<div") -> {
                            val divTag = currSubstring.substringBefore('>')
                            val curr = currSubstring.substringBefore("</div>").substringAfter('>')
                            append(
                                curr.twasNoNewline()
                            )
                            i += divTag.length + 1 + curr.length + 5
                        }

                        currSubstring.startsWith("<section") -> {
                            val curr = currSubstring.substringBefore('>')
                            i += curr.length
                        }

                        currSubstring.startsWith("<noinclude>") -> {
                            val curr =
                                currSubstring.substringBefore("</noinclude>").substringAfter('>')
                            append(curr.twasNoNewline())
                            i += 11 + curr.length + 11
                        }

                        currSubstring.startsWith("<code>") -> {
                            val curr = currSubstring.substringBefore("</code>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 6 + curr.length + 6
                        }

                        currSubstring.startsWith("<syntaxhighlight") -> {
                            val curr =
                                currSubstring.substringBefore("</syntaxhighlight>")
                                    .substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr) }
                            i += currSubstring.substringBefore('>').length + curr.length + 18
                        }

                        currSubstring.startsWith("<sub>") -> {
                            val curr = currSubstring.substringBefore("</sub>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Subscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<var>") -> {
                            val curr = currSubstring.substringBefore("</var>").substringAfter('>')
                            append("''$curr''".twasNoNewline())
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<sup>") -> {
                            val curr = currSubstring.substringBefore("</sup>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Superscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<small>") -> {
                            val curr = currSubstring.substringBefore("</small>").substringAfter('>')
                            withStyle(SpanStyle(fontSize = (fontSize - 4).sp)) {
                                append(curr.twasNoNewline())
                            }
                            i += 7 + curr.length + 7
                        }

                        currSubstring.lowercase().startsWith("<math") -> {
                            val lowerSubstring = currSubstring.lowercase()
                            val closeIndex = lowerSubstring.indexOf("</math>")
                            if (closeIndex != -1) {
                                val openTag = currSubstring.substringBefore('>')
                                val content = currSubstring.substring(openTag.length + 1, closeIndex)
                                val isDisplay = openTag.lowercase().contains("display")

                                if (isDisplay) {
                                    append("\t\t")
                                }
                                
                                val converted = try {
                                    LaTeX2Unicode.convert(content).replace(' ', nbsp)
                                } catch (e: Exception) {
                                    content // Fallback to raw LaTeX if conversion fails
                                }
                                
                                withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                    append(converted)
                                }
                                i += closeIndex + 6 // +6 because loop will add +1
                            } else {
                                append(input[i])
                            }
                        }

                        currSubstring.startsWith("<blockquote") -> {
                            val curr =
                                currSubstring.substringBefore("</blockquote>").substringAfter('>')
                            withStyle(
                                ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 16.sp,
                                        restLine = 16.sp
                                    )
                                )
                            ) {
                                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                    append('\n')
                                    append(curr.twas())
                                    append('\n')
                                }
                            }
                            i += 12 + curr.length + 13
                        }

                        currSubstring.startsWith("<!--") -> {
                            val curr = currSubstring.substringMatchingParen('<', '>')
                            i += curr.length - 1
                        }

                        htmlTag.matchAt(currSubstring, 0) != null -> {
                            // Drop the tag itself and keep whatever it wraps. Wikipedia scatters
                            // invisible anchors through headings, and every tag the parser does
                            // not know was printed verbatim.
                            i += (htmlTag.matchAt(currSubstring, 0)?.value?.length ?: 1) - 1
                        }

                        else -> {
                            append(input[i])
                        }
                    }
                }

                '{' ->
                    if (input.getOrNull(i + 1) == '{') {
                        val currSubstring =
                            substringMatchingParen('{', '}', i).substringBeforeLast("}}")
                        when {
                            refTemplates.fastAny { item ->
                                currSubstring
                                    .startsWith(item, ignoreCase = true)
                                    .also { if (it) refTemplate = item }
                            } -> {
                                val text =
                                    if (currSubstring.isTemplate("${refTemplate.removePrefix("{{")} book")) {
                                        // Bracket-aware: splitting on every '|' cut parameters
                                        // whose value contains a nested template or link, and the
                                        // remainder of the citation was printed raw.
                                        val params =
                                            citationParameters(currSubstring).toMutableMap()

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        val title = params["title"]?.let { titleText ->
                                            "''$titleText''" +
                                                    (params["edition"]?.let { " ($it ed.)" } ?: "")
                                        }

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })",
                                            title,
                                            params["publisher"],
                                            params["isbn"]?.let { "[[ISBN]] $it" }
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else if (
                                        currSubstring.isTemplate("${refTemplate.removePrefix("{{")} web") ||
                                        currSubstring.isTemplate("${refTemplate.removePrefix("{{")} news") ||
                                        currSubstring.isTemplate("${refTemplate.removePrefix("{{")} AV media") ||
                                        currSubstring.isTemplate("${refTemplate.removePrefix("{{")} press release")
                                    ) {
                                        // Bracket-aware: splitting on every '|' cut parameters
                                        // whose value contains a nested template or link, and the
                                        // remainder of the citation was printed raw.
                                        val params =
                                            citationParameters(currSubstring).toMutableMap()

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        // `title += …` on a null title concatenated the string
                                        // "null" into the citation, so a source with only a
                                        // translated title rendered as `null [Translated title]`.
                                        var title = params["title"]?.let { titleText ->
                                            "''$titleText''" +
                                                    (params["edition"]?.let { " ($it ed.)" } ?: "")
                                        }
                                        val transTitle = params["trans-title"]
                                        if (transTitle != null)
                                            title = ((title ?: "") + " [$transTitle]").trim()

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })".takeIf { it.trim('(', ')').isNotBlank() },
                                            if (params["url"] == null) title.takeIf {
                                                it?.trim('"')?.isNotBlank() == true
                                            }
                                            else "[${params["url"]} $title]".takeIf {
                                                it.trim('"').isNotBlank()
                                            },
                                            params["website"],
                                            "''${params["work"] ?: ""}''".takeIf {
                                                it.trim('\'').isNotBlank()
                                            },
                                            params["publisher"],
                                            "[${params["archive-url"]} Archived] ${params["archive-date"]}".takeIf { params["archive-url"] != null },
                                            "via ${params["via"]}".takeIf { params["via"] != null }
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else if (currSubstring.isTemplate("${refTemplate.removePrefix("{{")} journal")
                                    ) {
                                        // Bracket-aware: splitting on every '|' cut parameters
                                        // whose value contains a nested template or link, and the
                                        // remainder of the citation was printed raw.
                                        val params =
                                            citationParameters(currSubstring).toMutableMap()

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        val title = params["title"]?.let { titleText ->
                                            "''$titleText''" +
                                                    (params["edition"]?.let { " ($it ed.)" } ?: "")
                                        }
                                        val volume =
                                            "'''${params["volume"]}'''${" (${params["issue"]})".takeIf { params["issue"] != null } ?: ""}${": ${params["pages"]}".takeIf { params["pages"] != null } ?: ""}".takeIf { params["volume"] != null }

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })",
                                            if (params["url"] == null) title.takeIf {
                                                it?.trim('"')?.isNotBlank() == true
                                            }
                                            else "[${params["url"]} $title]".takeIf {
                                                it.trim('"').isNotBlank()
                                            },
                                            "''${params["journal"]}''".takeIf { params["journal"] != null },
                                            volume,
                                            params["publisher"],
                                            "[[ISSN]] ${params["issn"]}".takeIf { params["issn"] != null },
                                            "[[Bibcode]]:${params["bibcode"]}".takeIf { params["bibcode"] != null },
                                            "[[Doi_(identifier)|doi]]:${params["doi"]}".takeIf { params["doi"] != null },
                                            "[[PMC]]:${params["PMC"]}".takeIf { params["PMC"] != null },
                                            "[[PMID]]:${params["PMID"]}".takeIf { params["PMID"] != null },
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else {
                                        // Wikipedia has dozens of cite variants beyond the three
                                        // handled above; unhandled ones used to fall through to
                                        // their own raw wikitext.
                                        renderCitation(currSubstring)?.twas()
                                            ?: AnnotatedString("")
                                    }
                                append(text)
                            }

                            currSubstring.trimEnd() in escapeTemplates -> {
                                append(escapeTemplates.getValue(currSubstring.trimEnd()))
                            }

                            currSubstring.isTemplate("abbr") -> {
                                val curr = currSubstring.substringAfter('|', "").substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.isTemplate("TableTBA") -> {
                                append("<small>TBA</small>".twas())
                            }

                            currSubstring.isTemplate("efn") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                curr.twas()
                            }

                            currSubstring.isTemplate("convert") ||
                                    currSubstring.isTemplate("cvt")
                                -> {
                                val curr = currSubstring.substringAfter('|', "")
                                val currSplit = curr.split('|')
                                var toAdd = ""
                                if (curr.isNotEmpty()) {
                                    toAdd += currSplit[0]
                                    val part1 = currSplit.getOrNull(1)
                                    toAdd += if (part1 in listOf("-", "to", "and")) {
                                        " " + part1 + " " + (currSplit.getOrNull(2) ?: "") + " " + (currSplit.getOrNull(3) ?: "")
                                    } else {
                                        if (part1 != null) " $part1" else ""
                                    }
                                }
                                append(toAdd)
                            }

                            // The pipe is required so that {{chembox}}, {{chem-stub}} and
                            // friends do not get treated as inline formulae.
                            currSubstring.isTemplate("chem2|") -> {
                                appendChem2(
                                    currSubstring.substringAfter('|', "")
                                        .splitTemplateParameters()
                                        .firstOrNull()
                                        ?.plainLinkText()
                                        .orEmpty(),
                                    fontSize
                                )
                            }

                            currSubstring.isTemplate("chem|") -> {
                                appendChem(
                                    currSubstring.substringAfter('|', "")
                                        .splitTemplateParameters()
                                        .map { it.plainLinkText() },
                                    fontSize
                                )
                            }

                            currSubstring.isTemplate("mono|") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.isTemplate("math") ||
                                    currSubstring.isTemplate("mvar")
                                -> {
                                val curr = currSubstring.substringAfter('|', "").removePrefix("1=")
                                withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                    append(curr.replace(' ', nbsp).twas())
                                }
                            }

                            currSubstring.isTemplate("val") -> {
                                val curr = currSubstring.substringAfter('|', "").substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.isTemplate("var") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append("''$curr''".twas())
                            }

                            arrayOf("{{small", "{{smaller", "{{petit", "{{hw-small", "{{sma").any {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|', "")
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.isTemplate("main") -> {
                                val links =
                                    hatnoteLinks(currSubstring.substringAfter('|').split('|'))
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Main article")
                                    if (links.size > 1) append("s: ")
                                    else append(": ")
                                    links.fastForEachIndexed { index, (target, label) ->
                                        append(
                                            "[[$target|${label.replace("#", " § ")}]]".twas()
                                        )
                                        if (index == links.size - 2 && links.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < links.size - 1) append(", ")
                                    }
                                }
                                append('\n')
                            }

                            currSubstring.isTemplate("see also") -> {
                                val links = hatnoteLinks(
                                    currSubstring.substringAfter('|').split('|')
                                        .filterNot { it.startsWith('#') }
                                )
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("See also: ")
                                    links.fastForEachIndexed { index, (target, label) ->
                                        append(
                                            "[[$target|${label.replace("#", " § ")}]]".twas()
                                        )
                                        if (index == links.size - 2 && links.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < links.size - 1) append(", ")
                                    }
                                }
                            }

                            currSubstring.isTemplate("date") -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                if (splitList.size < 3) {
                                    append(splitList[0])
                                } else {
                                    append(splitList[0])
                                    append(' ')
                                    append(splitList[1])
                                    append(' ')
                                    append(splitList[2])
                                }
                            }

                            currSubstring.isTemplate("dfn") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append("'''$curr'''".twas())
                            }

                            currSubstring.isTemplate("distinguish") -> {
                                val textSpecified =
                                    currSubstring.contains("text=") || currSubstring.contains("text =")
                                val curr =
                                    if (textSpecified) currSubstring.substringAfter('=').trim()
                                    else currSubstring.substringAfter('|')
                                val splitList =
                                    if (textSpecified) listOf(curr)
                                    else curr.split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Not to be confused with ")
                                    if (!textSpecified) splitList.fastForEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(
                                                    MAGIC_SEP
                                                ).replace("#", " § ")
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", or "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    } else append(splitList[0].twas())
                                    append('.')
                                }
                            }

                            currSubstring.isTemplate("redirect-distinguish") -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                append("\"${splitList.getOrNull(0)}\" redirects here; not to be confused with ")
                                splitList.subList(1, splitList.size)
                                    .fastForEachIndexed { index: Int, it: String ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(
                                                    MAGIC_SEP
                                                )
                                            }]]".twas()
                                        )

                                        if (index == splitList.size - 2 && splitList.size > 2) append(
                                            ", or "
                                        )
                                        else if (index < splitList.size - 2) append(", ")
                                    }
                                append('.')
                            }

                            currSubstring.isTemplate("format price") -> {
                                val curr =
                                    currSubstring.substringAfter('|', "").substringBefore('|')
                                append(
                                    if (curr.contains('.')) curr.toDoubleOrNull()
                                        ?.formatToHumanReadable() ?: curr.twas()
                                    else curr.toLongOrNull()?.formatToHumanReadable() ?: curr.twas()
                                )
                            }

                            currSubstring.isTemplate("for") -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                    .fastFilter { !it.contains('=') }
                                if (splitList.size > 1) {
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                        append("For ${splitList[0]}, see ")
                                        splitList.subList(1, splitList.size)
                                            .fastForEachIndexed { index: Int, it: String ->
                                                append(
                                                    "[[${it.substringBefore(MAGIC_SEP)}|${
                                                        it.substringAfter(
                                                            MAGIC_SEP
                                                        )
                                                    }]]".twas()
                                                )

                                                if (index == splitList.size - 2 && splitList.size > 2) append(
                                                    ", and "
                                                )
                                                else if (index < splitList.size - 2) append(", ")
                                            }
                                        append('.')
                                    }
                                }
                            }

                            currSubstring.isTemplate("about") -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    when {
                                        currSubstring.matches("\\{\\{[Aa]bout\\|\\|[^|]+\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|\\|\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''For other uses, see [[${
                                                    splitList.last().replace(MAGIC_SEP, "|")
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|[^|]+\\|\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For other uses, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|[^|]+\\|\\|[^|]+\\|and\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For other uses, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]] and [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+\\|and\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]] and [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches("\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+".toRegex()) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]. For ${splitList[3]}, see [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }
                                    }
                                }
                            }

                            currSubstring.isTemplate("US$") -> {
                                if (currSubstring.contains('|')) {
                                    val curr = currSubstring.substringAfter('|', "")
                                    append("US$${curr.twas()}")
                                } else append("US$")
                            }

                            currSubstring.isTemplate("USD") -> {
                                val amount = currSubstring.substringAfter('|', "").substringBefore('|')
                                append("\$$amount")
                            }

                            currSubstring.isTemplate("Euro") -> {
                                val amount = currSubstring.substringAfter('|', "").substringBefore('|')
                                append("€$amount")
                            }

                            currSubstring.isTemplate("JPY") -> {
                                val amount = currSubstring.substringAfter('|', "").substringBefore('|')
                                append("¥$amount")
                            }

                            currSubstring.isTemplate("GBP") -> {
                                val amount = currSubstring.substringAfter('|', "").substringBefore('|')
                                append("£$amount")
                            }

                            listOf("{{start date", "{{end date").fastAny {
                                currSubstring.startsWith(it, true)
                            } -> {
                                val params = currSubstring.substringAfter('|', "")
                                    .splitNotInBraces('|', '{', '}')
                                    .fastMap { it.trim() }
                                    .fastFilter { it.isNotEmpty() && !it.contains('=') }
                                val year = params.getOrNull(0)
                                val month = params.getOrNull(1)?.toIntOrNull()
                                val day = params.getOrNull(2)?.toIntOrNull()
                                when {
                                    year != null && month != null && month in 1..12 && day != null ->
                                        append("${monthNames[month - 1]} $day, $year")

                                    year != null && month != null && month in 1..12 ->
                                        append("${monthNames[month - 1]} $year")

                                    year != null -> append(year)
                                }
                            }

                            currSubstring.isTemplate("hatnote") -> {
                                val curr = currSubstring.substringAfter('|', "").replace('\n', ' ')
                                append("''$curr''".twas())
                            }

                            currSubstring.isTemplate("IPAc-en") -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString("")
                                append("/${curr.replace(' ', nbsp)}/")
                            }

                            currSubstring.isTemplate("langx") -> {
                                val lang = langCodeToName(
                                    currSubstring.substringAfter('|').substringBefore('|').trim()
                                )
                                val text =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                        .filterNot { it.contains('=') }.joinToString()
                                append("[[$lang|$lang]]: ".twas())
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(
                                        text.twas()
                                    )
                                }
                            }

                            currSubstring.isTemplate("lang|") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.substringAfter('|').substringBefore('|').twas())
                                }
                            }

                            currSubstring.isTemplate("transliteration") -> {
                                val curr = currSubstring.substringAfterLast('|', "")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.isTemplate("IPA") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append("${langCodeToName(curr.substringBefore('|'))}: ")
                                }
                                append(
                                    "[${
                                        curr.substringAfter('|').substringBefore("|")
                                            .replace("|", "").replace(' ', nbsp)
                                    }]"
                                )
                            }

                            currSubstring.isTemplate("respell") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.replace('|', '-'))
                                }
                            }

                            currSubstring.isTemplate("BCE") -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr)
                                append(nbsp)
                                append("BCE")
                            }

                            currSubstring.isTemplate("blockquote") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(
                                    ParagraphStyle(
                                        textIndent = TextIndent(
                                            firstLine = 16.sp,
                                            restLine = 16.sp
                                        )
                                    )
                                ) {
                                    withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                        append('\n')
                                        append(
                                            curr.substringAfter("text=")
                                                .substringBefore('=')
                                                .substringBeforeLast('|')
                                                .twas()
                                        )
                                        append('\n')
                                    }
                                }
                            }

                            currSubstring.isTemplate("further") -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|').fastFilter { !it.contains('=') }
                                val topic = curr.substringAfter("topic=", "").substringBefore('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Further")
                                    if (topic.isNotEmpty()) {
                                        append(" information on $topic")
                                        append(
                                            ": [[${
                                                curr.substringAfter('|').substringBefore('|')
                                                    .substringBefore('#')
                                            }]]\n".twas()
                                        )
                                    } else {
                                        append(" reading")
                                        append(
                                            ": [[${splitList.getOrNull(0)}]]\n".twas()
                                        )
                                    }
                                }
                            }

                            currSubstring.isTemplate("rp") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append("<sup>:$curr </sup>".twas())
                            }

                            currSubstring.isTemplate("isbn") -> {
                                val curr = currSubstring.substringAfter('|', "").split('|')
                                    .filterNot { it.contains('=') }.joinToString()
                                append("[[ISBN]] $curr".twas())
                            }

                            currSubstring.isTemplate("sfrac") -> {
                                val splitList = currSubstring.substringAfter('|', "")
                                    .splitTemplateParameters()
                                when (splitList.size) {
                                    3 -> append("${splitList[0]}<sup>${splitList[1]}</sup>/<sub>${splitList[2]}</sub>".twas())
                                    2 -> append("<sup>${splitList[0]}</sup>/<sub>${splitList[1]}</sub>".twas())
                                    1 -> append("<sup>1</sup>/<sub>${splitList[0]}</sub>".twas())
                                }
                            }

                            currSubstring.isTemplate("as of") -> {
                                val curr = currSubstring.substringAfter("{{")
                                append(curr.substringBefore('|'))
                                append(' ')
                                var date = ""
                                curr.substringAfter('|').split('|').fastForEach {
                                    if (it.toIntOrNull() != null) {
                                        date += it
                                        date += '/'
                                    }
                                }
                                append(date.trim('/'))
                            }

                            currSubstring.isTemplate("unichar") -> {
                                val curr =
                                    currSubstring.substringAfter('|', "").substringBefore('|')
                                append("<code>U+$curr</code> ".twas())
                                try {
                                    append(Character.toString(curr.toInt(16)))
                                } catch (_: Exception) {
                                }
                            }

                            currSubstring.isTemplate("char") -> {
                                append(currSubstring.substringAfter('|', "").twas())
                            }

                            currSubstring.isTemplate("Nihongo") -> {
                                val params = currSubstring.substringAfter('|', "")
                                    .splitTemplateParameters()
                                    .map { it.trim() }
                                    .fastFilter { it.isNotEmpty() && !it.contains('=') }
                                if (params.isNotEmpty()) {
                                    append(params.first().twas())
                                    val rest = params.drop(1)
                                    if (rest.isNotEmpty())
                                        append(" (${rest.joinToString(", ")})".twas())
                                }
                            }

                            currSubstring.isTemplate("flagg") -> {
                                val curr = currSubstring.substringAfter('|').substringAfter('|')
                                    .substringBefore('|')

                                when (curr.length) {
                                    3 -> append(
                                        countryFlag(alpha3CodeToAlpha2CodeMap[curr] ?: "")
                                                + ' '
                                    )

                                    else -> append(
                                        countryFlag(countryNameToAlpha2CodeMap[curr] ?: "")
                                                + ' '
                                    )
                                }
                                append(curr.twas())
                            }

                            currSubstring.isTemplate("noflag") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append(curr.twas())
                            }

                            listOf("{{nowrap", "{{nobr", "{{nobreak", "{{nwr", "{{nbr").fastAny {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append(curr.replace(' ', nbsp).twas())
                            }

                            currSubstring.startsWith(
                                "{{spaced en dash",
                                ignoreCase = true
                            ) || currSubstring.isTemplate("snd")
                                -> {
                                append(nbsp)
                                append(ndash)
                                append(' ')
                            }

                            currSubstring.isTemplate("empty section") -> {
                                withStyle(
                                    SpanStyle(
                                        fontStyle = FontStyle.Italic,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("This section is empty. You can help by adding to it on Wikipedia.")
                                }
                            }

                            currSubstring.startsWith("{{\"|") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.twas())
                                }
                            }

                            listOf("{{birth date", "{{death date").fastAny {
                                currSubstring.startsWith(it)
                            } -> {
                                val splitList = currSubstring
                                    .substringAfter('|')
                                    .splitNotInBraces('|')
                                    .fastFilter { !it.contains("df|mf".toRegex()) }
                                    .fastMap { it.substringAfter('=').trim() }

                                append(
                                    splitList
                                        .take(min(splitList.size, 3))
                                        .joinToString("/")
                                        .twas()
                                )
                            }

                            currSubstring.isTemplate("unbulleted list") -> {
                                val splitList = currSubstring
                                    .substringAfter('|', "")
                                    .splitNotInBraces('|', '{', '}')
                                    .fastFilter { !it.trim().matches("[a-zA-Z_-]+\\s*=.*".toRegex()) }
                                    .fastMap { it.trim() }
                                    .joinToString("\n")

                                append(splitList.twas())
                            }

                            listOf(
                                "{{bulleted list",
                                "{{ulist",
                                "{{blist",
                                "{{bulleted",
                                "{{unordered list"
                            ).fastAny {
                                currSubstring.startsWith(it, true)
                            } -> {
                                val splitList = currSubstring
                                    .substringAfter('|', "")
                                    .splitNotInBraces('|')
                                    .fastMap {
                                        if (it.trim()
                                                .matches("\\d+=.+".toRegex(RegexOption.DOT_MATCHES_ALL))
                                        )
                                            it.substringAfter('=').trim()
                                        else it.trim()
                                    }
                                    .joinToString("\n* ")

                                append("* $splitList".twas())
                            }

                            currSubstring.isTemplate("Transcluded section") -> {
                                val title =
                                    currSubstring.split('|').filter { it.contains("source=") }
                                        .joinToString().substringAfter('=')
                                append("''This section is transcluded from [[$title]]''".twas())
                            }

                            currSubstring.isTemplate("fb") -> {
                                val country = currSubstring.substringAfter('|').substringBefore('|')

                                when (country.length) {
                                    3 -> append(
                                        countryFlag(
                                            alpha3CodeToAlpha2CodeMap[fifaCodeToAlpha3CodeMap[country.uppercase()]
                                                ?: country] ?: ""
                                        ) + " $country"
                                    )

                                    else -> append(
                                        countryFlag(
                                            countryNameToAlpha2CodeMap[country] ?: ""
                                        ) + " $country"
                                    )
                                }
                            }

                            currSubstring.isTemplate("citation needed") -> {
                                append("<sup>[citation needed]</sup>".twas())
                            }

                            currSubstring.isTemplate("url") -> {
                                val curr = currSubstring.substringAfter('|', "")
                                if (curr.matches(".+://.+".toRegex()))
                                    append("[$curr ${curr.substringAfter("//")}]".twas())
                                else append("[https://$curr $curr]".twas())
                            }

                            currSubstring.isTemplate("Starbox begin") -> {
                                val templateLength = currSubstring.length
                                i = input.indexOf(
                                    "{{starbox end}}",
                                    i,
                                    ignoreCase = true
                                ) - templateLength + 15
                            }

                            currSubstring.startsWith(
                                "{{lahirmati",
                                true
                            ) -> { // Indonesian template for date of birth and death
                                val curr =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                if (curr.size <= 3) {
                                    append("lahir " + curr.joinToString("/").twas())
                                } else append(
                                    "${
                                        curr.subList(0, min(curr.size, 3)).joinToString("/").twas()
                                    } $ndash ${curr.subList(3, curr.size).joinToString("/")}"
                                )
                            }

                            listOf("{{siglo", "{{segle").fastAny {
                                currSubstring.startsWith(it, true)
                            } -> {
                                // Spanish/Catalan template for century
                                val first = currSubstring.substringAfter("{{").substringBefore('|')
                                val second = currSubstring.substringAfter('|').substringBefore('|')
                                append("$first <small>$second</small>".twas())
                            }

                            currSubstring.isTemplate("romanes") -> {
                                // Spanish/Catalan template for roman numerals
                                val second = currSubstring.substringAfter('|').substringBefore('|')
                                append("<small>$second</small>".twas())
                            }

                            currSubstring.isTemplate("tracce") -> {
                                // Italian template for music track lists
                                val splitList = currSubstring.substringAfter('|')
                                    .split('|')
                                    .fastMap { it.substringAfter('=').trim() }
                                    .chunked(2)

                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    splitList.fastForEach {
                                        append("${it.getOrNull(0)} $mdash ${it.getOrNull(1)}\n")
                                    }
                                }
                            }

                            currSubstring.isTemplate("reflist") -> {
                                val reflist = refListIndex
                                    .toSortedMap()
                                    .map { "${it.key}.\t\t${it.value}" }

                                reflist.fastForEach {
                                    withStyle(
                                        ParagraphStyle(
                                            textIndent = TextIndent(restLine = 27.sp),
                                            lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        )
                                    ) { append(it.twas()) }
                                }
                            }

                            else -> {
                                val curr = input.getOrNull(i + 1 + currSubstring.length + 1)
                                if (curr == '\n') i++
                            }
                        }
                        i += currSubstring.length + 1
                    } else append(input[i])

                '&' ->
                    if (input.substring(i, min(i + 10, input.length)).contains(';')) {
                        val curr = input.substring(i, input.indexOf(';', i) + 1)
                        append(AnnotatedString.fromHtml(curr))
                        i += curr.length - 1
                    } else append(input[i])

                ':' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine && !inIndentCode) {
                        val curr = input.listItemAt(i)
                        val depth = curr.takeWhile { it == ':' }.length
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(
                                    firstLine = (12 * depth).sp,
                                    restLine = (12 * depth).sp
                                ),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append(curr.dropWhile { it == ':' }.trim().twas())
                        }
                        i += curr.length
                    } else append(input[i])

                '*' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substring(i).substringBefore(' ').count { it == '*' }
                        val curr = input.listItemAt(i)
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (12 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append(bullet)
                            append("\t\t")
                            append(curr.dropWhile { it == '*' }.trim().twas())
                        }
                        i += curr.length
                    } else append(input[i])

                '#' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substring(i).substringBefore(' ').count { it == '#' }
                        val curr = input.listItemAt(i)
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (27 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append("$number.")
                            append("\t\t")
                            append(curr.dropWhile { it == '#' }.trim().twas())
                        }
                        i += curr.length
                        number++
                    } else append(input[i])

                '=' ->
                    if (input.getOrNull(i + 1) == '=' && input.getOrNull(i + 2) == '=') {
                        if (input.getOrNull(i + 3) == '=') {
                            if (input.getOrNull(i + 4) == '=') { // h5
                                val curr = input.substring(i + 5).substringBefore("=====")
                                withStyle(typography.titleMedium.toSpanStyle()) {
                                    append(curr.trim().twas())
                                }
                                i += 4 + curr.length + 5
                            } else { // h4
                                val curr = input.substring(i + 4).substringBefore("====")
                                withStyle(typography.titleLarge.toSpanStyle()) {
                                    append("${curr.trim()}\n".twas())
                                }
                                i += 3 + curr.length + 4
                            }
                        } else { // h3
                            val curr = input.substring(i + 3).substringBefore("===")
                            withStyle(typography.headlineSmall.toSpanStyle()) {
                                append("${curr.trim()}\n".twas())
                            }
                            i += 2 + curr.length + 3
                        }
                    } else append(input[i])

                '\'' ->
                    if (input.getOrNull(i + 1) == '\'' && input.getOrNull(i + 2) == '\'') {
                        if (!bold) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            bold = true
                        } else {
                            pop()
                            bold = false
                        }
                        i += 2
                    } else if (input.getOrNull(i + 1) == '\'') {
                        if (!italic) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            italic = true
                        } else {
                            pop()
                            italic = false
                        }
                        i += 1
                    } else append(input[i])

                '[' ->
                    // Malformed source doubles the brackets ([[[[Property::Page|Page]]]]); treat
                    // the extra pair as part of the same link rather than as literal text.
                    if (input.startsWith("[[[[", i)) i += 1
                    else if (input.getOrNull(i + 1) == '[') {
                        val curr = input.substring(i + 2).substringBefore("]]")
                        if (!curr.startsWith("File:", ignoreCase = true)) {
                            // Semantic MediaWiki annotates links as [[property::value]], which
                            // PsychonautWiki uses heavily; only the value is shown. The leading
                            // colon of [[:Category:X]] is markup too, and was being shown when
                            // the link had no separate label.
                            val rawTarget = curr.substringBefore('|')
                                .substringAfterLast("::")
                                .removePrefix(":")
                            val target = rawTarget.linkArgument()
                            val label = if ('|' in curr) curr.substringAfter('|').linkArgument()
                            else rawTarget
                            withLink(
                                LinkAnnotation.Url(
                                    "",
                                    TextLinkStyles(
                                        SpanStyle(color = colorScheme.primary)
                                    )
                                ) {
                                    loadPage(target.substringBefore('#'))
                                }
                            ) {
                                append(label.twas())
                            }
                            i += 1 + curr.length + 2
                        } else i += substringMatchingParen('[', ']', i).length - 1
                    } else if (input.getOrNull(i + 1) == 'h') {
                        val linkText = input.substringMatchingParen('[', ']', i)
                        withLink(
                            LinkAnnotation.Url(
                                "",
                                TextLinkStyles(
                                    SpanStyle(color = colorScheme.primary)
                                )
                            ) {
                                showRef(linkText.substringBefore(' ').removePrefix("["))
                            }
                        ) {
                            append(
                                linkText.substringAfter(' ').removeSuffix("]").trim()
                                    .twasNoNewline()
                            )
                            append(" \uD83D\uDD17")
                        }
                        i += linkText.length - 1
                    } else append(input[i])

                ']' ->
                    if (input.getOrNull(i + 1) == ']') i += 1
                    else append(input[i])

                else -> append(input[i])
            }
            i++
        }
    }
    } finally {
        WikitextParserState.recursionDepth.set(currentDepth)
    }
}

/**
 * The text of the list item starting at [start]: everything up to the first line break that is not
 * inside a template or a link.
 *
 * Wikipedia writes bibliography entries as one bullet holding a citation spread over a dozen
 * lines. Cutting at the first newline left the parser with `* {{Cite journal` and spilled every
 * parameter of the template into the article as raw wikitext.
 */
internal fun String.listItemAt(start: Int): String {
    var braceDepth = 0
    var bracketDepth = 0
    var i = start

    while (i < length) {
        when {
            startsWith("{{", i) -> { braceDepth++; i += 2 }
            startsWith("}}", i) -> { if (braceDepth > 0) braceDepth--; i += 2 }
            startsWith("[[", i) -> { bracketDepth++; i += 2 }
            startsWith("]]", i) -> { if (bracketDepth > 0) bracketDepth--; i += 2 }
            this[i] == '\n' && braceDepth == 0 && bracketDepth == 0 -> return substring(start, i)
            else -> i++
        }
    }
    return substring(start)
}

fun String.substringMatchingParen(
    openingParen: Char,
    closingParen: Char,
    startIndex: Int = 0
): String {
    var i = startIndex
    var stack = 0
    while (i < length) {
        if (this[i] == openingParen) stack++
        else if (this[i] == closingParen) stack--

        if (stack == 0) break

        i++
    }

    // Unbalanced input: return what is left from startIndex. Returning the whole string instead
    // handed callers the text preceding the opening bracket as if it were part of the element, and
    // let them advance their cursor by more characters than they had actually consumed.
    return if (i < length) this.substring(startIndex, i + 1)
    else this.substring(startIndex.coerceIn(0, length))
}

fun String.buildRefList() {
    var i = 0
    while (i < this.length) {
        if (this[i] == '<') {
            if (this.substring(i, min(i + 10, this.length)).startsWith("<ref name")) {
                val open = this.substringMatchingParen('<', '>', i)
                if (!open.endsWith("/>")) {
                    val refWt = this.substring(i).substringBefore("</ref>").substringAfter('>')
                    val refName = open.substringAfter("name=").removeSuffix(">").trim('"', ' ')
                    refList[refName] = refWt
                }
            }
        }
        i++
    }
}

fun String.splitNotInBraces(delimiter: Char, open: Char = '[', close: Char = ']'): List<String> {
    var stack = 0
    val out = mutableListOf<String>()
    var curr = ""

    for (c in this) {
        if (c == open) stack++
        else if (c == close) stack--

        if (c == delimiter && stack == 0) {
            out.add(curr)
            curr = ""
        } else {
            curr += c
        }
    }

    if (curr.isNotEmpty()) {
        out.add(curr)
    }

    return out
}

object ReferenceData {
    var refCount = 1
    val refList = mutableMapOf<String, String>()
    val refListIndex = mutableMapOf<Int, String>()
    val refListCount = mutableMapOf<String, Int>()
    var refTemplate = "{{cite"
    // "{{citation" is the bare {{citation}} template: it shares no prefix with "{{cite", so
    // without it here those references were dropped from the article entirely.
    val refTemplates = listOf("{{cite", "{{citation", "{{lien", "{{cita|")
    val infoboxTemplates = listOf("{{infobox", "{{taxobox", "{{Automatic taxobox", "{{Картка")

    /**
     * Clears the per-article reference state.
     *
     * Every field here is global, so anything left behind leaks into the next article that is
     * parsed. [refListIndex] in particular backs {{reflist}}: while it was not being cleared, an
     * article's reference list kept the trailing entries of every longer article read before it.
     */
    fun reset() {
        refCount = 1
        refList.clear()
        refListIndex.clear()
        refListCount.clear()
        refTemplate = "{{cite"
    }
}