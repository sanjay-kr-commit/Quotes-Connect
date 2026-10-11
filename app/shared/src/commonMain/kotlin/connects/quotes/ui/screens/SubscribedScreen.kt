package connects.quotes.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import connects.quotes.ui.theme.UserIcon

@Composable
fun SubscribedScreen(
    subscribedItems: List<Passage>,
    onInspectPassage: (Id) -> Unit,
    onToggleSubscribe: (Id) -> Unit,
    onOpenIdentityDialog: () -> Unit
) {
    val currentIdentity = SessionManager.currentAuthorId
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(subscribedItems, searchQuery) {
        subscribedItems.filter { p ->
            searchQuery.isBlank() ||
                p.content.contains(searchQuery, ignoreCase = true) ||
                p.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                p.id.value.contains(searchQuery, ignoreCase = true)
        }
    }

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
            Column {
                Text(
                    text = "DIRECT SUBSCRIPTIONS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Directly followed passages and thinkers without citing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (currentIdentity != null) {
                Text(
                    text = "${subscribedItems.size} FOLLOWING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (currentIdentity == null) {
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
                        text = "SOVEREIGN IDENTITY REQUIRED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Subscribing directly to authors and passages allows you to curate a personal knowledge feed without algorithmic manipulation. Connect or create an identity to subscribe.",
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
        } else {
            // Search and Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter subscribed thoughts or #tags...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(4.dp),
                singleLine = true
            )

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (subscribedItems.isEmpty()) "NO SUBSCRIPTIONS YET" else "NO MATCHING PASSAGES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (subscribedItems.isEmpty())
                                "Click '☆ SUBSCRIBE' on any billboard passage to follow thoughts directly."
                            else "Try clearing your search query.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id.value }) { passage ->
                        PassageCard(
                            passage = passage,
                            onInspect = { onInspectPassage(passage.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
