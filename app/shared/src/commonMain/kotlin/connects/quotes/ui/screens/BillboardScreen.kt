package connects.quotes.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import connects.quotes.client.SessionManager
import connects.quotes.model.BillboardItem
import connects.quotes.model.Id
import connects.quotes.model.Tag
import connects.quotes.ui.components.PassageCard
import connects.quotes.ui.components.TagChip
import connects.quotes.ui.theme.BillboardIcon

@Composable
fun BillboardScreen(
    items: List<BillboardItem>,
    topTags: List<Tag>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    onInspectPassage: (Id) -> Unit,
    onToggleAdvertise: (Id) -> Unit
) {
    val currentAuthorId = SessionManager.currentAuthorId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BillboardIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "THE BILLBOARD",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${items.size} BROADCASTS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Public knowledge commons. Displays voluntary broadcasts and mandatory orphan passages without algorithmic curation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tag Filters
        if (topTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(
                    tag = "ALL",
                    isSelected = selectedTag == null,
                    onClick = { onSelectTag(null) }
                )
                topTags.forEach { tag ->
                    TagChip(
                        tag = "${tag.name} (${tag.count})",
                        isSelected = selectedTag == tag.name,
                        onClick = { onSelectTag(tag.name) }
                    )
                }
            }
        }

        // Billboard Items List
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No broadcasts found. Be the first to create and advertise a passage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(items, key = { it.passage.id.value }) { item ->
                    PassageCard(
                        passage = item.passage,
                        isOrphan = item.isOrphan,
                        isAdvertised = true,
                        isOwner = currentAuthorId != null,
                        onInspect = { onInspectPassage(item.passage.id) },
                        onToggleAdvertise = { onToggleAdvertise(item.passage.id) },
                        onTagClick = { onSelectTag(it) }
                    )
                }
            }
        }
    }
}
