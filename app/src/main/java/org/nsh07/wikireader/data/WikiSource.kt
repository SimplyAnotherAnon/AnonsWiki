package org.nsh07.wikireader.data

/**
 * The app addresses a wiki by the code it would use for a Wikipedia language ("en", "de", …).
 * PsychonautWiki is not a Wikipedia language, so it gets a reserved code of its own and travels
 * through the app the same way a language does: search, history, saved articles, image URLs and
 * request routing all keep working without knowing it is a different wiki.
 */
const val PSYCHONAUT_WIKI_LANG = "psychonautwiki"

const val PSYCHONAUT_WIKI_HOST = "psychonautwiki.org"

const val PSYCHONAUT_WIKI_NAME = "PsychonautWiki"

/** The host serving the wiki identified by [lang]. */
fun wikiHost(lang: String): String =
    if (lang == PSYCHONAUT_WIKI_LANG) PSYCHONAUT_WIKI_HOST else "$lang.wikipedia.org"

/** The article URL for [title] on the wiki identified by [lang]. */
fun wikiArticleUrl(lang: String, title: String): String =
    "https://${wikiHost(lang)}/wiki/$title"

fun isPsychonautWiki(lang: String?): Boolean = lang == PSYCHONAUT_WIKI_LANG
