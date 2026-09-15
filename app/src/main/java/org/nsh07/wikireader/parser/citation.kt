package org.nsh07.wikireader.parser

/**
 * Reads the `|name = value` pairs of a citation template.
 *
 * Splitting respects nested templates and links, so a parameter whose value contains a pipe
 * (`|title=[[Foo|Bar]]`) is not cut in half.
 */
internal fun citationParameters(template: String): Map<String, String> =
    template.substringAfter('|', "")
        .splitTemplateParameters()
        .mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2) pair[0].trim().lowercase() to pair[1].trim() else null
        }
        .filter { (_, value) -> value.isNotBlank() }
        .toMap()

/** Container parameters, in the order a citation would name them. */
private val containerKeys = listOf(
    "work", "journal", "magazine", "newspaper", "periodical", "encyclopedia", "encyclopaedia",
    "website", "conference", "series", "booktitle", "book-title", "dictionary"
)

/** Identifier parameters and the label each is shown with. */
private val identifierLabels = listOf(
    "isbn" to "[[ISBN]] ",
    "issn" to "[[ISSN]] ",
    "doi" to "[[Doi_(identifier)|doi]]:",
    "pmid" to "[[PMID]]:",
    "pmc" to "[[PMC]]:",
    "bibcode" to "[[Bibcode]]:",
    "oclc" to "[[OCLC]] ",
    "jstor" to "[[JSTOR]] ",
    "arxiv" to "[[arXiv]]:",
)

/**
 * Renders any citation template the specific handlers do not cover — `{{cite magazine}}`,
 * `{{cite encyclopedia}}`, `{{cite thesis}}`, `{{cite court}}`, `{{citation}}` and the rest.
 *
 * Wikipedia has dozens of these and adds more, and the unhandled ones used to fall through to
 * their own raw wikitext, so reference lists and bibliographies showed the reader
 * `{{cite magazine |last=Lizza |first=Ryan …}}` verbatim.
 *
 * @return the citation as wikitext, or null when there is nothing worth showing
 */
internal fun renderCitation(template: String): String? {
    val params = citationParameters(template)
    if (params.isEmpty()) return null

    val author = listOfNotNull(
        params["last"] ?: params["last1"] ?: params["author"] ?: params["author1"]
        ?: params["authors"] ?: params["vauthors"] ?: params["editor"] ?: params["veditors"],
        params["first"] ?: params["first1"]
    ).joinToString().trim()

    val date = listOfNotNull(params["date"], params["year"]).firstOrNull()

    val titleText = params["script-title"] ?: params["title"] ?: params["chapter"]
        ?: params["litigants"] ?: params["entry"]
    val title = titleText?.let {
        "''$it''" + (params["edition"]?.let { edition -> " ($edition ed.)" } ?: "")
    }
    val transTitle = params["trans-title"]?.let { "[$it]" }

    val container = containerKeys.firstNotNullOfOrNull { params[it] }

    val volume = params["volume"]?.let { volume ->
        "'''$volume'''" + (params["issue"]?.let { " ($it)" } ?: "")
    } ?: params["issue"]?.let { "($it)" }

    val pages = (params["pages"] ?: params["page"])?.let { "pp. $it" }

    val identifiers = identifierLabels.mapNotNull { (key, label) ->
        params[key]?.let { "$label$it" }
    }

    val head = when {
        author.isNotEmpty() && date != null -> "$author ($date)"
        author.isNotEmpty() -> author
        date != null -> "($date)"
        else -> null
    }

    val parts = listOfNotNull(head, title, transTitle, container, params["publisher"], volume, pages) +
            identifiers

    val rendered = parts.filter { it.isNotBlank() }.joinToString(". ").trim()
    return if (rendered.isEmpty()) null else "$rendered."
}
