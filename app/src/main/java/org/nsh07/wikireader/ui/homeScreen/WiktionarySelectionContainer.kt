package org.nsh07.wikireader.ui.homeScreen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import org.nsh07.wikireader.R

private object WiktionaryMenuKey

/**
 * A [SelectionContainer] whose selection toolbar offers a Wiktionary lookup for the selected text.
 *
 * Long-pressing a word selects it, so this gives the article text the "long press a word to look it
 * up in Wiktionary" behaviour asked for in upstream issue #326, without taking over the long-press
 * gesture that selection and copying rely on.
 *
 * @param lang Wikipedia language code of the article; the lookup uses the matching Wiktionary.
 */
@Composable
fun WiktionarySelectionContainer(
    lang: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val selectionState = rememberSelectionState()
    val context = LocalContext.current
    val label = stringResource(R.string.lookUpInWiktionary)

    SelectionContainer(
        selectionState,
        modifier.appendTextContextMenuComponents {
            item(key = WiktionaryMenuKey, label = label) {
                val selection = selectionState.selectedTexts
                    .joinToString(separator = "") { it.text }
                    .trim()
                if (selection.isNotEmpty()) openWiktionary(context, lang, selection)
                close()
            }
        },
        content
    )
}

/**
 * Opens [term] on the Wiktionary edition for [lang], falling back to the English edition for
 * languages that have no Wiktionary of their own.
 */
private fun openWiktionary(context: Context, lang: String, term: String) {
    val uri = "https://${lang.ifBlank { "en" }}.wiktionary.org/wiki/${Uri.encode(term)}".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Log.e("Wiktionary", "No app available to open $uri", e)
    }
}
