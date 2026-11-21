/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.SearchHistory

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.onlineSearchSuggestions(
    history: List<SearchHistory>,
    suggestions: List<String>,
    pureBlack: Boolean,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onQueryChange: (TextFieldValue) -> Unit,
) {
    if (history.isNotEmpty()) {
        item(
            key = "history_header",
            contentType = "section_header",
        ) {
            SearchSectionHeader(
                title = stringResource(R.string.search_history),
                pureBlack = pureBlack,
                modifier = Modifier.animateItem(),
            )
        }

        itemsIndexed(
            items = history,
            key = { index, historyItem -> "history_${historyItem.id}_${historyItem.query}_$index" },
            contentType = { _, _ -> "history" },
        ) { _, historyItem ->
            SuggestionItem(
                query = historyItem.query,
                online = false,
                onClick = {
                    onSearch(historyItem.query)
                    onDismiss()
                },
                onDelete = {
                    onDeleteHistory(historyItem)
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(historyItem.query, TextRange(historyItem.query.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }
    }

    if (suggestions.isNotEmpty()) {
        item(
            key = "suggestions_header",
            contentType = "section_header",
        ) {
            SearchSectionHeader(
                title = stringResource(R.string.suggestions),
                pureBlack = pureBlack,
                modifier = Modifier.animateItem(),
            )
        }

        itemsIndexed(
            items = suggestions,
            key = { index, suggestion -> "suggestion_${suggestion}_$index" },
            contentType = { _, _ -> "suggestion" },
        ) { _, suggestion ->
            SuggestionItem(
                query = suggestion,
                online = true,
                onClick = {
                    onSearch(suggestion)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(suggestion, TextRange(suggestion.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    val isDark = pureBlack || isSystemInDarkTheme()
    val containerColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val iconContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconContainerColor,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    painterResource(if (online) R.drawable.ic_search else R.drawable.history),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (!online) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.remove_from_history),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(
                onClick = onFillTextField,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_top_left),
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
