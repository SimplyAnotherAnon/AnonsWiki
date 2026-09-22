package org.nsh07.wikireader.data

import kotlinx.serialization.Serializable

@Serializable
data class ExpandTemplatesResponse(
    val expandtemplates: ExpandedWikitext = ExpandedWikitext()
)

@Serializable
data class ExpandedWikitext(
    val wikitext: String = ""
)
