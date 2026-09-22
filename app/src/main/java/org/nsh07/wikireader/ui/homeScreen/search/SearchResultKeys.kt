package org.nsh07.wikireader.ui.homeScreen.search

/**
 * A stable, unique key for a search result row.
 *
 * Results from more than one wiki share a list, and the same title exists on several of them —
 * searching "LSD" returns a page of that name from both Wikipedia and PsychonautWiki. Keying on
 * the title alone gives two rows the same key, which a lazy list rejects outright.
 *
 * @param lang the wiki the result came from, null for the one being read
 * @param title the result's page title
 * @param kind distinguishes the suggestion list from the full results list
 */
fun searchResultKey(lang: String?, title: String, kind: String): String =
    "${lang.orEmpty()}:$title-$kind"
