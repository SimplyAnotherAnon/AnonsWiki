package org.nsh07.wikireader.network

import org.nsh07.wikireader.data.ExpandTemplatesResponse
import org.nsh07.wikireader.data.FeedApiResponse
import org.nsh07.wikireader.data.WikiApiPageData
import org.nsh07.wikireader.data.WikiApiPrefixSearchResults
import org.nsh07.wikireader.data.WikiApiSearchResults
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

private const val API_QUERY =
    "w/api.php?format=json&action=query&prop=pageimages|description|langlinks&piprop=thumbnail|original&pithumbsize=320&pilicense=any&lllimit=max&redirects=1&formatversion=2&maxage=900&smaxage=900"

private const val CONTENT_QUERY =
    "wiki/{title}?action=raw&maxage=900&smaxage=900"

private const val EXPAND_TEMPLATES_QUERY =
    "w/api.php?action=expandtemplates&prop=wikitext&format=json&formatversion=2&maxage=900&smaxage=900"

private const val FEED_QUERY =
    "api/rest_v1/feed/featured/{date}"

private const val PREFIX_SEARCH_QUERY =
    "w/api.php?action=query&generator=prefixsearch&prop=pageimages|pageterms&piprop=thumbnail&pithumbsize=128&wbptterms=description&gpslimit=20&format=json&formatversion=2&maxage=900&smaxage=900"

private const val RANDOM_QUERY =
    "w/api.php?format=json&action=query&prop=pageimages|pageterms|langlinks&piprop=original&pilicense=any&lllimit=max&generator=random&redirects=1&formatversion=2&grnnamespace=0"

private const val SEARCH_QUERY =
    "w/api.php?action=query&generator=search&prop=pageimages&piprop=thumbnail&pithumbsize=128&gsrnamespace=0&gsrlimit=20&gsrprop=snippet|titlesnippet|redirecttitle|extensiondata&format=json&formatversion=2&maxage=900&smaxage=900"

/**
 * Every call takes a `host`. A non-null value routes that one request to the named Wikipedia;
 * null leaves it on the app-wide host. See [org.nsh07.wikireader.network.HostSelectionInterceptor].
 */
interface WikipediaApiService {
    @GET(PREFIX_SEARCH_QUERY)
    suspend fun getPrefixSearchResults(
        @Query("gpssearch") query: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): WikiApiPrefixSearchResults

    @GET(SEARCH_QUERY)
    suspend fun getSearchResults(
        @Query("gsrsearch") query: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): WikiApiSearchResults

    @GET(API_QUERY)
    suspend fun getPageData(
        @Query("titles") query: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): WikiApiPageData

    @GET(CONTENT_QUERY)
    suspend fun getPageContent(
        @Path("title", encoded = true) title: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): String

    /**
     * The page's wikitext with its templates expanded, for wikis that keep article content in
     * transcluded templates.
     */
    @GET(EXPAND_TEMPLATES_QUERY)
    suspend fun getExpandedPageContent(
        @Query("title") title: String,
        @Query("text") text: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): ExpandTemplatesResponse

    @GET(RANDOM_QUERY)
    suspend fun getRandomResult(
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): WikiApiPageData

    @GET(FEED_QUERY)
    suspend fun getFeed(
        @Path("date", encoded = true) date: String,
        @Header(HostSelectionInterceptor.HOST_HEADER) host: String?
    ): FeedApiResponse
}
