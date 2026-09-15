package org.nsh07.wikireader.ui.homeScreen

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.WikiLang
import org.nsh07.wikireader.data.langCodeToName
import org.nsh07.wikireader.ui.settingsScreen.LanguageSearchBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArticleLanguageBottomSheet(
    langs: List<WikiLang>,
    recentLangs: List<String>,
    currentLang: WikiLang,
    searchStr: String,
    searchQuery: String,
    setShowSheet: (Boolean) -> Unit,
    setLang: (String) -> Unit,
    loadPage: (String) -> Unit,
    setSearchStr: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState()

    val articleLangs =
        remember(recentLangs, langs) { langs.partition { it.lang in recentLangs } }

    // Resolve the display names and apply the filter up front. Filtering inside the item lambda
    // instead leaves one zero-height lazy item per non-matching language; with 300+ Wikipedia
    // languages that makes the list compose almost every entry to fill the viewport and the
    // sheet visibly flickers while typing.
    val recentMatches = remember(articleLangs.first, searchQuery) {
        articleLangs.first.mapNotNull { it.withDisplayName() }
            .filter { (_, name) -> name.contains(searchQuery, ignoreCase = true) }
    }
    val otherMatches = remember(articleLangs.second, searchQuery) {
        articleLangs.second.mapNotNull { it.withDisplayName() }
            .filter { (_, name) -> name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = {
            setShowSheet(false)
            setSearchStr("")
        },
        sheetState = bottomSheetState,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.chooseWikipediaLanguage),
                style = typography.labelLarge
            )
            LanguageSearchBar(
                searchStr = searchStr,
                setSearchStr = setSearchStr,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(shapes.largeIncreased)
            ) {
                item {
                    Text(
                        stringResource(R.string.currentLanguage),
                        style = typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
                item {
                    val langName: String? = try {
                        langCodeToName(currentLang.lang)
                    } catch (_: Exception) {
                        Log.e("Language", "Language not found: ${currentLang.lang}")
                        null
                    }
                    LanguageListItem(
                        headlineContent = { Text(langName ?: currentLang.lang) },
                        supportingContent = { Text(currentLang.title) },
                        selected = true,
                        items = 1,
                        index = 0
                    ) {
                        scope.launch { bottomSheetState.hide() }
                            .invokeOnCompletion {
                                if (!bottomSheetState.isVisible) {
                                    setShowSheet(false)
                                }
                            }
                    }
                }
                if (recentMatches.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.recentLanguages),
                            style = typography.titleSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    itemsIndexed(
                        recentMatches,
                        key = { _: Int, it: Pair<WikiLang, String> -> "recent-" + it.first.lang }
                    ) { index, (wikiLang, langName) ->
                        LanguageListItem(
                            headlineContent = { Text(langName) },
                            supportingContent = { Text(wikiLang.title) },
                            selected = false,
                            items = recentMatches.size,
                            index = index
                        ) {
                            setLang(wikiLang.lang)
                            loadPage(wikiLang.title)
                            scope
                                .launch { bottomSheetState.hide() }
                                .invokeOnCompletion {
                                    if (!bottomSheetState.isVisible) {
                                        setShowSheet(false)
                                        setSearchStr("")
                                    }
                                }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item {
                    Text(
                        stringResource(R.string.otherLanguages),
                        style = typography.titleSmall,
                        modifier = Modifier.padding(
                            top = 14.dp,
                            bottom = 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                    )
                }
                itemsIndexed(
                    otherMatches,
                    key = { _: Int, it: Pair<WikiLang, String> -> it.first.lang }
                ) { index, (wikiLang, langName) ->
                    LanguageListItem(
                        headlineContent = { Text(langName) },
                        supportingContent = { Text(wikiLang.title) },
                        selected = false,
                        items = otherMatches.size,
                        index = index
                    ) {
                        setLang(wikiLang.lang)
                        loadPage(wikiLang.title)
                        scope
                            .launch { bottomSheetState.hide() }
                            .invokeOnCompletion {
                                if (!bottomSheetState.isVisible) {
                                    setShowSheet(false)
                                    setSearchStr("")
                                }
                            }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }
}

/**
 * Pairs a [WikiLang] with its human-readable language name, or `null` if the code is unknown.
 */
private fun WikiLang.withDisplayName(): Pair<WikiLang, String>? = try {
    this to langCodeToName(lang)
} catch (_: Exception) {
    Log.e("Language", "Language not found: $lang")
    null
}
