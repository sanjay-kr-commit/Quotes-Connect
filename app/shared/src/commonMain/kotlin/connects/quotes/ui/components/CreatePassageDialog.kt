package connects.quotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import connects.quotes.client.ClientTagCache
import connects.quotes.client.SessionManager
import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.model.Tag
import connects.quotes.ui.theme.AddIcon
import connects.quotes.ui.theme.CloseIcon
import connects.quotes.ui.theme.LockIcon
import connects.quotes.ui.theme.QCRadiusButton
import connects.quotes.ui.theme.QCRadiusCard
import connects.quotes.ui.theme.QCRadiusInput
import kotlinx.coroutines.delay

@Composable
fun CreatePassageDialog(
    initialTargetId: Id? = null,
    onDismiss: () -> Unit,
    onSuggestTags: (suspend (String) -> List<Tag>)? = null,
    onSubmit: (type: PassageType, content: String, tags: List<String>, parentId: Id?, isRecordable: Boolean, shouldAdvertise: Boolean, subscribeToId: Id?) -> Unit
) {
    val currentAuthorId = SessionManager.currentAuthorId

    var selectedType by remember {
        mutableStateOf(if (initialTargetId != null) PassageType.COMMENT else PassageType.QUOTE)
    }
    var contentInput by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    var suggestedTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var claimOwnership by remember { mutableStateOf(currentAuthorId != null) }
    var shouldAdvertise by remember { mutableStateOf(false) }
    var isRecordable by remember { mutableStateOf(false) }
    var targetIdInput by remember { mutableStateOf(initialTargetId?.value ?: "") }

    val scrollState = rememberScrollState()

    fun addTag(raw: String) {
        val norm = Tag.normalize(raw)
        if (norm.isNotBlank() && !selectedTags.contains(norm)) {
            selectedTags = selectedTags + norm
        }
        tagInput = ""
        suggestedTags = emptyList()
    }

    // 500ms debounce: when typing tag, pull top suggestions for autocorrect based on search so far
    LaunchedEffect(tagInput) {
        val q = Tag.normalize(tagInput)
        if (q.isBlank()) {
            suggestedTags = emptyList()
            return@LaunchedEffect
        }
        // 500ms delay representing person thinking
        delay(500L)
        // Instant response from ClientTagCache (avoids call wastage on Desktop & Android)
        val localMatches = ClientTagCache.suggestTags(q, limit = 8)
            .filter { !selectedTags.contains(it.name) }
        suggestedTags = localMatches

        // If remote suggestion callback provided, fetch updated counts asynchronously
        if (onSuggestTags != null) {
            try {
                val remote = onSuggestTags(q).filter { !selectedTags.contains(it.name) }
                if (remote.isNotEmpty()) {
                    suggestedTags = remote
                }
            } catch (_: Throwable) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(QCRadiusCard)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AddIcon(size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "CREATE PASSAGE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        CloseIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Type Selector Row
                Text(
                    text = "PASSAGE TYPE:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PassageType.QUOTE, PassageType.PASSAGE, PassageType.COMMENT, PassageType.RECORDABLE, PassageType.SOURCE).forEach { type ->
                        val isSelected = selectedType == type
                        Text(
                            text = type.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    selectedType = type
                                    if (type == PassageType.RECORDABLE) isRecordable = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                // Target Passage ID (for Comment or Subscription)
                if (selectedType == PassageType.COMMENT || initialTargetId != null) {
                    OutlinedTextField(
                        value = targetIdInput,
                        onValueChange = { targetIdInput = it },
                        label = { Text("Target Passage UUID to Subscribe to") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Content Field
                OutlinedTextField(
                    value = contentInput,
                    onValueChange = { contentInput = it },
                    label = { Text("Passage Content / Thought / Quotation") },
                    placeholder = { Text("Enter knowledge or citation. #tags automatically recognized.") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )

                // Tags Section: Chips LazyRow + Input Field + 500ms Autocorrect Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Interactive LazyRow of Selected Tag Chips
                    if (selectedTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TAGS (${selectedTags.size}):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap × to remove",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            items(selectedTags) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        IconButton(
                                            onClick = { selectedTags = selectedTags.filter { it != tag } },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Text(
                                                text = "×",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Tag Input Field (detects space or comma to convert into chip button)
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { newVal ->
                            if (newVal.contains(" ") || newVal.contains(",") || newVal.contains("\n")) {
                                val parts = newVal.split(" ", ",", "\n")
                                val toAdd = parts.dropLast(1)
                                    .map { Tag.normalize(it) }
                                    .filter { it.isNotBlank() && !selectedTags.contains(it) }
                                if (toAdd.isNotEmpty()) {
                                    selectedTags = selectedTags + toAdd
                                }
                                tagInput = parts.last().trim()
                                suggestedTags = emptyList()
                            } else {
                                tagInput = newVal
                            }
                        },
                        label = { Text("Add Tag (type space to place chip)") },
                        placeholder = { Text("e.g. stoicism philosophy logic") },
                        trailingIcon = {
                            if (tagInput.isNotBlank()) {
                                IconButton(onClick = { addTag(tagInput) }) {
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 3. Autocorrect / Top Tag Suggestions Row (appears after 500ms delay)
                    if (suggestedTags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "TOP SUGGESTIONS (BY USAGE):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                items(suggestedTags) { suggestion ->
                                    Surface(
                                        onClick = { addTag(suggestion.name) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "#${suggestion.name}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "(${suggestion.count})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Ownership & Lineage Controls
                if (currentAuthorId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Claim Ownership at Creation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Server records your parent UUID. Never revealed to clients.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = claimOwnership,
                            onCheckedChange = { claimOwnership = it }
                        )
                    }
                }

                // Advertisement Control
                if (currentAuthorId == null) {
                    // Public Domain Orphan
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "PUBLIC DOMAIN ORPHAN PASSAGE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Unparented passages are automatically and permanently broadcast on the Billboard as public domain knowledge. They cannot be unadvertised.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Owner can choose to advertise
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Advertise to Billboard",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Broadcast to public square immediately.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = shouldAdvertise,
                            onCheckedChange = { shouldAdvertise = it }
                        )
                    }
                }

                // Recordable Toggle
                if (selectedType != PassageType.RECORDABLE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                LockIcon(size = 13.dp, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Make Recordable (Immutable)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "Once created, it cannot be deleted by anyone.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = isRecordable,
                            onCheckedChange = { isRecordable = it }
                        )
                    }
                }

                // Submit Button
                Button(
                    onClick = {
                        if (contentInput.isNotBlank()) {
                            val trailingTag = Tag.normalize(tagInput)
                            val finalTags = if (trailingTag.isNotBlank() && !selectedTags.contains(trailingTag)) {
                                selectedTags + trailingTag
                            } else {
                                selectedTags
                            }
                            val parent = if (claimOwnership) currentAuthorId else null
                            val subTo = if (targetIdInput.isNotBlank()) Id(targetIdInput.trim()) else null

                            onSubmit(
                                selectedType,
                                contentInput.trim(),
                                finalTags,
                                parent,
                                isRecordable || selectedType == PassageType.RECORDABLE,
                                shouldAdvertise || parent == null, // Orphans are auto-advertised!
                                subTo
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(QCRadiusButton)
                ) {
                    Text("SUBMIT PASSAGE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
