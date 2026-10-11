package connects.quotes.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import connects.quotes.client.SessionManager
import connects.quotes.model.Id
import connects.quotes.model.Passage
import connects.quotes.ui.components.PassageCard
import connects.quotes.ui.components.FeedGraphCanvas
import connects.quotes.ui.theme.FeedIcon
import connects.quotes.ui.theme.UserIcon

@Composable
fun FeedScreen(
    feedItems: List<Passage>,
    feedDepth: Int = 1,
    onDepthChange: (Int) -> Unit = {},
    onInspectPassage: (Id) -> Unit,
    onOpenIdentityDialog: () -> Unit
) {
    val currentIdentity = SessionManager.currentAuthorId
    var canvasMode by remember { mutableStateOf(false) }

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
                FeedIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "THE FEED",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentIdentity != null) {
                    Text(
                        text = "${feedItems.size} CONNECTED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = canvasMode,
                    onClick = { canvasMode = !canvasMode },
                    label = {
                        Text(
                            if (canvasMode) "FEED" else "CANVAS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    },
                    shape = RoundedCornerShape(4.dp)
                )
            }
        }

        // Depth Discovery Selector
        if (currentIdentity != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NETWORK DEPTH (LAZY DISCOVERY)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "DEPTH $feedDepth",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Lazily discovers child thoughts: creations by subscribed authors and citations attached to subscribed quotes.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            1 to "Depth 1 (Direct Children)",
                            2 to "Depth 2 (Deeper)",
                            3 to "Depth 3 (Network)"
                        ).forEach { (d, label) ->
                            FilterChip(
                                selected = feedDepth == d,
                                onClick = { onDepthChange(d) },
                                label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (currentIdentity == null) {
            // Prompt to create or connect identity
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserIcon(size = 32.dp, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "ANONYMOUS MODE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "The Feed aggregates passages you have subscribed to in the graph. Connect or generate an Author Passage to construct your personal knowledge network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenIdentityDialog,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("SETUP SOVEREIGN IDENTITY")
                    }
                }
            }
        } else if (feedItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your feed is empty. Explore the Billboard and subscribe to passages to build your network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (canvasMode) {
            FeedGraphCanvas(
                passages = feedItems,
                feedDepth = feedDepth,
                onDepthChange = onDepthChange,
                onNodeClick = onInspectPassage,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(feedItems, key = { it.id.value }) { passage ->
                    PassageCard(
                        passage = passage,
                        onInspect = { onInspectPassage(passage.id) }
                    )
                }
            }
        }
    }
}
