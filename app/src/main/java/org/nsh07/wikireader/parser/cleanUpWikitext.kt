package org.nsh07.wikireader.parser

/**
 * Remove/simplify parts of wikitext
 *
 * @param wikitext Source Wikitext to clean up
 */
fun cleanUpWikitext(wikitext: String): String {
    return convertHtmlTables(expandEpisodeTables(wikitext))
        .replace("<!--.+?-->".toRegex(), "")
        .replace("</?onlyinclude>".toRegex(RegexOption.IGNORE_CASE), "")
        .replace("</?noinclude>".toRegex(RegexOption.IGNORE_CASE), "")
        .replace(
            "<section\\s+(?:begin|end)\\s*=.*?/>".toRegex(RegexOption.IGNORE_CASE),
            ""
        )
        .replace("== \n", "==\n")
        // Convert colon-indented math to display math for proper block rendering
        // This ensures math like ": <math>formula</math>" is extracted as a block element
        .replace(
            "^:+\\s*<math(?![^>]*display)".toRegex(RegexOption.MULTILINE),
            "<math display=\"block\""
        )
        .replace(
            "\\{\\{nobility table header.*?\\}\\}"
                .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            "{| class=\"wikitable\"\n"
        )
}

/**
 * Converts [Episode table](https://en.wikipedia.org/wiki/Template:Episode_table) templates (used
 * on television season pages) into standard wikitables that the app can render.
 */
private fun expandEpisodeTables(wikitext: String): String {
    var result = wikitext
    var start = result.indexOf("{{Episode table", ignoreCase = true)

    while (start >= 0) {
        val template = result.substringMatchingParen('{', '}', start)
        if (!template.endsWith("}}")) break

        val table = buildString {
            append("{| class=\"wikitable\"\n")
            append("! No. !! Title !! Directed by !! Written by !! Original air date\n")

            var episodeStart = template.indexOf("{{Episode list", ignoreCase = true)
            while (episodeStart >= 0) {
                val episode = template.substringMatchingParen('{', '}', episodeStart)
                if (!episode.endsWith("}}")) break

                val params = episode.removePrefix("{{").removeSuffix("}}")
                    .splitTemplateParameters()
                    .mapNotNull {
                        val parts = it.split('=', limit = 2)
                        if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim()
                        else null
                    }
                    .toMap()

                val overall = params["episodenumber"] ?: ""
                val inSeason = params["episodenumber2"] ?: ""
                val number =
                    if (inSeason.isNotEmpty() && inSeason != overall) "$overall ($inSeason)"
                    else overall
                val title = params["title"] ?: ""

                append("|-\n")
                append("| $number\n")
                append("| ${if (title.isNotEmpty()) "\"$title\"" else ""}\n")
                append("| ${params["directedby"] ?: ""}\n")
                append("| ${params["writtenby"] ?: ""}\n")
                append("| ${params["originalairdate"] ?: ""}\n")

                episodeStart = template.indexOf(
                    "{{Episode list",
                    episodeStart + episode.length,
                    ignoreCase = true
                )
            }
            append("|}")
        }
        result = result.replaceRange(start, start + template.length, table)
        start = result.indexOf("{{Episode table", start + table.length, ignoreCase = true)
    }

    return result
}

/**
 * Splits template parameters on `|`, ignoring pipes nested in `{{...}}` templates and
 * `[[...]]` links.
 */
internal fun String.splitTemplateParameters(): List<String> {
    val out = mutableListOf<String>()
    var braceDepth = 0
    var bracketDepth = 0
    var curr = StringBuilder()

    for (c in this) {
        when (c) {
            '{' -> braceDepth++
            '}' -> braceDepth--
            '[' -> bracketDepth++
            ']' -> bracketDepth--
        }
        if (c == '|' && braceDepth == 0 && bracketDepth == 0) {
            out.add(curr.toString())
            curr = StringBuilder()
        } else curr.append(c)
    }
    if (curr.isNotEmpty()) out.add(curr.toString())

    return out
}

/** Matches a table-structure tag, capturing its name and attributes. */
private val tableTag =
    "</?(table|caption|tr|th|td)\\b([^<>]*)>".toRegex(RegexOption.IGNORE_CASE)

/** Severity classes used by substance-box interaction rows, and the label each stands for. */
private val severityClasses = listOf(
    "SBInteractionDangerous" to "Dangerous",
    "SBInteractionUnsafe" to "Unsafe",
    "SBInteractionUncertain" to "Caution",
    "SBInteractionLowRisk" to "Low risk",
    "SBInteractionCaution" to "Caution",
)

/** The attributes a wikitable cell carries through; the rest are presentation. */
private val keptCellAttributes = "\\b(colspan|rowspan)\\s*=\\s*\"?(\\d+)\"?".toRegex(RegexOption.IGNORE_CASE)

/**
 * Rewrites HTML tables as wikitables.
 *
 * MediaWiki accepts both spellings and templates routinely emit the HTML one — PsychonautWiki's
 * substance box, which carries the routes of administration, dosages and durations, is an HTML
 * `<table>`. The app renders only wikitables, so those tables were invisible.
 *
 * Nested tables are converted from the inside out, and cell content is flattened onto one line
 * because the wikitable parser reads a table line by line.
 */
internal fun convertHtmlTables(wikitext: String): String {
    if (!wikitext.contains("<table", ignoreCase = true)) return wikitext

    val out = StringBuilder()
    var i = 0
    while (i < wikitext.length) {
        val start = wikitext.indexOf("<table", i, ignoreCase = true)
        if (start < 0) {
            out.append(wikitext, i, wikitext.length)
            break
        }
        out.append(wikitext, i, start)

        val end = matchingTableEnd(wikitext, start)
        if (end < 0) { // Unbalanced: leave the rest alone rather than guess.
            out.append(wikitext, start, wikitext.length)
            break
        }
        out.append(htmlTableToWikitable(wikitext.substring(start, end)))
        i = end
    }
    return out.toString()
}

/** The index just past the `</table>` closing the table that opens at [start]. */
private fun matchingTableEnd(text: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < text.length) {
        when {
            text.startsWith("<table", i, ignoreCase = true) -> depth++
            text.startsWith("</table", i, ignoreCase = true) -> {
                depth--
                val close = text.indexOf('>', i)
                if (close < 0) return -1
                if (depth == 0) return close + 1
                i = close
            }
        }
        i++
    }
    return -1
}

private fun htmlTableToWikitable(table: String): String {
    val rows = StringBuilder("\n{| class=\"wikitable\"\n")
    var cell: StringBuilder? = null
    var cellPrefix = ""
    var depth = 0
    var i = 0

    fun flushCell() {
        val content = cell?.toString()?.replace("\\s+".toRegex(), " ")?.trim().orEmpty()
        if (cell != null) rows.append(cellPrefix).append(content).append('\n')
        cell = null
    }

    while (i < table.length) {
        val match = tableTag.find(table, i)
        if (match == null) {
            cell?.append(table, i, table.length)
            break
        }
        cell?.append(table, i, match.range.first)

        val name = match.groupValues[1].lowercase()
        val closing = match.value.startsWith("</")
        val rawAttributes = match.groupValues[2]
        val attributes = keptCellAttributes.findAll(rawAttributes)
            .joinToString(" ") { "${it.groupValues[1].lowercase()}=${it.groupValues[2]}" }
        val severity = severityClasses
            .firstOrNull { (cls, _) -> rawAttributes.contains(cls, ignoreCase = true) }
            ?.second

        when {
            // A nested table's rows are folded into the parent. The alternative, a wikitable
            // inside a cell, renders as its own markup because a cell is laid out as text.
            name == "table" && !closing -> { depth++; flushCell() }

            name == "table" && closing -> { depth--; flushCell() }

            name == "tr" && !closing -> { flushCell(); rows.append("|-\n") }

            name == "caption" && !closing -> { flushCell(); cell = StringBuilder(); cellPrefix = "|+ " }

            name == "th" && !closing -> {
                flushCell()
                cell = StringBuilder()
                cellPrefix = if (attributes.isEmpty()) "! " else "! $attributes | "
                if (severity != null) cell?.append("$severity: ")
            }

            name == "td" && !closing -> {
                flushCell()
                cell = StringBuilder()
                cellPrefix = if (attributes.isEmpty()) "| " else "| $attributes | "
                if (severity != null) cell?.append("$severity: ")
            }

            closing -> flushCell()
        }
        i = match.range.last + 1
    }
    flushCell()

    rows.append("|}\n")
    return rows.toString()
}
