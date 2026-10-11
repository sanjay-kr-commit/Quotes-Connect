package connects.quotes.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import connects.quotes.client.SessionManager
import connects.quotes.model.Id
import connects.quotes.model.Passage
import connects.quotes.model.PassageType
import connects.quotes.ui.components.PassageCard
import connects.quotes.ui.components.TagChip
import connects.quotes.ui.theme.AddIcon
import connects.quotes.ui.theme.ArrowBackIcon
import connects.quotes.ui.theme.CopyIcon
import connects.quotes.ui.theme.LinkIcon
import connects.quotes.ui.theme.LockIcon
import connects.quotes.ui.theme.QCRadiusButton
import connects.quotes.ui.theme.QCRadiusCard
import connects.quotes.ui.theme.QCRadiusInput

@Composable
fun PassageDetailScreen(
    passage: Passage,
    subscribers: List<Passage>,
    subscribedTo: List<Passage>,
    treeItems: List<connects.quotes.graph.GraphEngine.FlatTreeItem> = emptyList(),
    isOrphan: Boolean,
    isAdvertised: Boolean,
    navigationStack: List<Id> = emptyList(),
    onBack: () -> Unit,
    onInspectPassage: (Id) -> Unit,
    onToggleAdvertise: () -> Unit,
    onReplyOrSubscribe: (Id) -> Unit
) {
    val currentAuthorId = SessionManager.currentAuthorId
    val clipboardManager = LocalClipboardManager.current

    var isVisualGraphMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Navigation Back Button & Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        ArrowBackIcon(tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "PASSAGE INSPECTOR",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { onReplyOrSubscribe(passage.id) }) {
                        AddIcon(tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Breadcrumb Lineage Trail
                if (navigationStack.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navigationStack.forEachIndexed { index, id ->
                            val isCurrent = id == passage.id
                            Text(
                                text = id.value.take(6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { if (!isCurrent) onInspectPassage(id) }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                            if (index < navigationStack.size - 1) {
                                Text("→", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        // Primary Passage Focus Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(QCRadiusCard)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(QCRadiusCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[${passage.type.name}]",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (passage.isRecordable || passage.type == PassageType.RECORDABLE) {
                                LockIcon(tint = MaterialTheme.colorScheme.primary, size = 14.dp)
                                Text(
                                    text = "RECORDABLE LEDGER",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Copy ID button
                        Row(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                .clickable { clipboardManager.setText(AnnotatedString(passage.id.value)) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CopyIcon(size = 11.dp, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = passage.id.value,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = passage.content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Action row: Copy content and tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (passage.tags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                passage.tags.forEach { tag ->
                                    TagChip(tag = tag)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(passage.content)) },
                            shape = RoundedCornerShape(QCRadiusButton),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CopyIcon(size = 11.dp, tint = MaterialTheme.colorScheme.primary)
                                Text("COPY PASSAGE", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                    // Billboard Status & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOrphan) {
                            Text(
                                text = "STATUS: SOVEREIGN COMMONS (PERMANENT)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (currentAuthorId != null) {
                            Button(
                                onClick = onToggleAdvertise,
                                shape = RoundedCornerShape(QCRadiusButton),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isAdvertised) "REMOVE FROM BILLBOARD" else "ADVERTISE TO BILLBOARD",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Text(
                                text = if (isAdvertised) "STATUS: ADVERTISED" else "STATUS: PRIVATE GRAPH",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { onReplyOrSubscribe(passage.id) },
                            shape = RoundedCornerShape(QCRadiusButton),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+ CITE / COMMENT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Citations & Reaction Tree (Linux Tree Command Hierarchy)
        val effectiveTreeItems = if (treeItems.isNotEmpty()) {
            treeItems
        } else {
            subscribers.mapIndexed { idx, sub ->
                val isLast = (idx == subscribers.size - 1)
                val prefix = if (isLast) "└── " else "├── "
                connects.quotes.graph.GraphEngine.FlatTreeItem(sub, prefix, depth = 1, isLast = isLast)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinkIcon(size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isVisualGraphMode) "GRAPH CANVAS (${subscribers.size})" else "CITATION TREE (${effectiveTreeItems.size})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { isVisualGraphMode = false },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = if (!isVisualGraphMode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("TREE", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = { isVisualGraphMode = true },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = if (isVisualGraphMode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("CANVAS", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Text(
                    text = if (isVisualGraphMode) "Interactive topological node-and-edge visual graph canvas:" else "Hierarchy of all passages citing or replying to this thread node:",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isVisualGraphMode) {
            item {
                connects.quotes.ui.components.VisualGraphCanvas(
                    rootPassage = passage,
                    connectedPassages = subscribers,
                    onNodeClick = onInspectPassage
                )
            }
        } else if (effectiveTreeItems.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "└──",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "(No citations connected to this thread yet)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onReplyOrSubscribe(passage.id) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+ CITE / REPLY", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
            items(effectiveTreeItems, key = { "${it.prefix}_${it.passage.id.value}" }) { item ->
                CitationTreeNode(
                    item = item,
                    onInspect = { onInspectPassage(item.passage.id) },
                    onReply = { onReplyOrSubscribe(item.passage.id) }
                )
            }
        }

        // Section: References / Outgoing Links
        if (subscribedTo.isNotEmpty()) {
            item {
                Text(
                    text = "REFERENCED PASSAGES (${subscribedTo.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(subscribedTo, key = { it.id.value }) { target ->
                PassageCard(
                    passage = target,
                    onInspect = { onInspectPassage(target.id) }
                )
            }
        }
    }
}

@Composable
fun CitationTreeNode(
    item: connects.quotes.graph.GraphEngine.FlatTreeItem,
    onInspect: () -> Unit,
    onReply: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Linux tree branch connector in monospace font
        Text(
            text = item.prefix,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Tree node content card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${item.passage.type.name}]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.passage.isRecordable) {
                            Text(
                                text = "• RECORDABLE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .clickable { clipboardManager.setText(AnnotatedString(item.passage.id.value)) }
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CopyIcon(size = 9.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = item.passage.id.value.take(8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = item.passage.content,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.passage.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.passage.tags.take(3).forEach { tag ->
                                Text(
                                    text = "#$tag",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COPY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { clipboardManager.setText(AnnotatedString(item.passage.content)) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "+ CITE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onReply() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
