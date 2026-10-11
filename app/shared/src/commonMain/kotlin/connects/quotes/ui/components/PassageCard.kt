package connects.quotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import connects.quotes.model.Passage
import connects.quotes.model.PassageType
import connects.quotes.ui.theme.CopyIcon
import connects.quotes.ui.theme.LinkIcon
import connects.quotes.ui.theme.LockIcon
import connects.quotes.ui.theme.QCRadiusButton
import connects.quotes.ui.theme.QCRadiusCard
import connects.quotes.ui.theme.QCRadiusInput

@Composable
fun PassageCard(
    passage: Passage,
    isOrphan: Boolean = false,
    isAdvertised: Boolean = false,
    isOwner: Boolean = false,
    onInspect: () -> Unit,
    onToggleAdvertise: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(QCRadiusCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Type badge & Ledger/Lock indicator
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
                        text = "[${passage.type.name}]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (passage.isRecordable || passage.type == PassageType.RECORDABLE) {
                        LockIcon(tint = MaterialTheme.colorScheme.primary, size = 14.dp)
                        Text(
                            text = "RECORDABLE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Provenance & Quick Copy Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy ID Chip
                    Row(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(passage.id.value))
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CopyIcon(size = 10.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = passage.id.value.take(8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Copy Passage Content Chip
                    Row(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(passage.content))
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CopyIcon(size = 10.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "COPY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Body Content
            Text(
                text = passage.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tags
            if (passage.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    passage.tags.take(4).forEach { tag ->
                        TagChip(tag = tag, onClick = { onTagClick?.invoke(tag) })
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

            // Footer: DAG connections & Billboard advertisement status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subscription stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinkIcon(size = 13.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${passage.subscriberIds.size} ${if (passage.subscriberIds.size == 1) "CITATION" else "CITATIONS"}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (passage.subscribedToIds.isNotEmpty()) {
                        Text(
                            text = "→ ${passage.subscribedToIds.size} REFS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Billboard action/status
                when {
                    isOrphan -> {
                        Text(
                            text = "SOVEREIGN COMMONS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    isOwner && onToggleAdvertise != null -> {
                        OutlinedButton(
                            onClick = onToggleAdvertise,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text(
                                text = if (isAdvertised) "UNADVERTISE" else "ADVERTISE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                    isAdvertised -> {
                        Text(
                            text = "ADVERTISED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
