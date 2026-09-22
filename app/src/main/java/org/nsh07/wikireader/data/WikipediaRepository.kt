package org.nsh07.wikireader.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.network.WikipediaApiService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val sectionTransclusion =
    "\\{\\{#section:\\s*([^|}]+)\\|\\s*([^}]+)\\}\\}".toRegex(RegexOption.IGNORE_CASE)

internal val pageTransclusion = "\\{\\{:\\s*([^{}|#]+?)\\s*\\}\\}".toRegex()

/**
 * Each call takes an optional `host`. Passing one pins that request to a particular Wikipedia,
 * which work running alongside the article being read needs so it cannot be redirected by, or
 * redirect, a concurrent request. Leaving it null uses the app-wide host.
 */
interface WikipediaRepository {
    suspend fun getPrefixSearchResults(query: String, host: String? = null): WikiApiPrefixSearchResults
    suspend fun getSearchResults(query: String, host: String? = null): WikiApiSearchResults
    suspend fun getPageData(query: String, host: String? = null): WikiApiPageData
    suspend fun getPageContent(title: String, host: String? = null): String
    suspend fun getRandomResult(host: String? = null): WikiApiPageData
    suspend fun getFeed(
        // Locale.ROOT keeps the date ASCII: the value goes straight into the feed URL, and
        // locales that default to non-Latin digits would otherwise build an invalid path.
        date: String = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)),
        host: String? = null
    ): FeedApiResponse
}

class NetworkWikipediaRepository(
    private val wikipediaApiService: WikipediaApiService,
    private val wikipediaPageApiService: WikipediaApiService,
    private val ioDispatcher: CoroutineDispatcher
) : WikipediaRepository {
    override suspend fun getPrefixSearchResults(query: String, host: String?): WikiApiPrefixSearchResults =
        withContext(ioDispatcher) {
            wikipediaApiService.getPrefixSearchResults(query, host)
        }

    override suspend fun getSearchResults(query: String, host: String?): WikiApiSearchResults =
        withContext(ioDispatcher) {
            wikipediaApiService.getSearchResults(query, host)
        }

    override suspend fun getPageData(query: String, host: String?): WikiApiPageData =
        withContext(ioDispatcher) {
            wikipediaApiService.getPageData(query, host)
        }

    override suspend fun getPageContent(title: String, host: String?): String =
        withContext(ioDispatcher) {
            // Transclusions are fetched from the same wiki as the page that transcludes them.
            val fetch: suspend (String) -> String = {
                wikipediaPageApiService.getPageContent(it, host)
            }
            expandPageTransclusions(
                expandSectionTransclusions(fetchPageWikitext(title, host), fetch),
                fetch
            )
        }

    /**
     * The page's wikitext.
     *
     * Wikis that keep article content in transcluded templates are asked to expand them first:
     * on PsychonautWiki the substance box, the effect lists and the experience reports are all
     * template calls, so the raw wikitext of an article is mostly `{{SubstanceBox/LSD}}` and
     * little else. Wikipedia is read raw, since the app renders its templates itself and knows
     * more about them than a server-side expansion would leave behind.
     */
    private suspend fun fetchPageWikitext(title: String, host: String?): String =
        if (host == PSYCHONAUT_WIKI_HOST) {
            runCatching {
                // The expansion answers JSON, so it goes through the JSON-converting service
                // rather than the raw-text one used for action=raw.
                wikipediaApiService
                    .getExpandedPageContent(title, "{{:$title}}", host)
                    .expandtemplates.wikitext
                    .ifBlank { null }
            }.getOrNull() ?: wikipediaPageApiService.getPageContent(title, host)
        } else wikipediaPageApiService.getPageContent(title, host)

    override suspend fun getRandomResult(host: String?): WikiApiPageData =
        withContext(ioDispatcher) {
            wikipediaApiService.getRandomResult(host)
        }

    override suspend fun getFeed(
        date: String,
        host: String?
    ): FeedApiResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getFeed(date, host)
        }
}

internal suspend fun expandSectionTransclusions(
    wikitext: String,
    getPageContent: suspend (String) -> String
): String {
    val pages = mutableMapOf<String, String>()

    return buildString {
        var lastIndex = 0
        sectionTransclusion.findAll(wikitext).forEach { match ->
            append(wikitext, lastIndex, match.range.first)
            val page = match.groupValues[1].trim().replace(' ', '_')
            val section = match.groupValues[2].trim()
            val pageContent = pages[page] ?: runCatching {
                getPageContent(page)
            }.getOrNull()?.also { pages[page] = it }
            append(pageContent?.extractSection(section) ?: match.value)
            lastIndex = match.range.last + 1
        }
        append(wikitext, lastIndex, wikitext.length)
    }
}

/**
 * Replaces whole-page transclusions (`{{:Page name}}`) with the target page's `<onlyinclude>`
 * content, mirroring MediaWiki's transclusion behaviour for pages such as TV season articles.
 */
internal suspend fun expandPageTransclusions(
    wikitext: String,
    getPageContent: suspend (String) -> String
): String {
    val pages = mutableMapOf<String, String>()

    return buildString {
        var lastIndex = 0
        pageTransclusion.findAll(wikitext).forEach { match ->
            append(wikitext, lastIndex, match.range.first)
            val page = match.groupValues[1].trim().replace(' ', '_')
            val pageContent = pages[page] ?: runCatching {
                getPageContent(page)
            }.getOrNull()?.also { pages[page] = it }
            append(pageContent?.extractOnlyInclude() ?: "")
            lastIndex = match.range.last + 1
        }
        append(wikitext, lastIndex, wikitext.length)
    }
}

internal fun String.extractOnlyInclude(): String? {
    val blocks = "<onlyinclude>(.*?)</onlyinclude>"
        .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(this)
        .map { it.groupValues[1] }
        .toList()
    return if (blocks.isEmpty()) null else blocks.joinToString("\n")
}

internal fun String.extractSection(name: String): String? {
    val escapedName = Regex.escape(name)
    val begin = "<section\\s+begin\\s*=\\s*[\"']?$escapedName[\"']?\\s*/>"
        .toRegex(RegexOption.IGNORE_CASE).find(this) ?: return null
    val end = "<section\\s+end\\s*=\\s*[\"']?$escapedName[\"']?\\s*/>"
        .toRegex(RegexOption.IGNORE_CASE).find(this, begin.range.last + 1) ?: return null
    return substring(begin.range.last + 1, end.range.first)
}