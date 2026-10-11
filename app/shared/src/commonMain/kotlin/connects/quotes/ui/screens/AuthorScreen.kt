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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import connects.quotes.model.AuthorDiscardAction
import connects.quotes.model.AuthorSettings
import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.model.InactivityCheckResult
import connects.quotes.model.Passage
import connects.quotes.ui.components.PassageCard
import connects.quotes.ui.theme.CopyIcon
import connects.quotes.ui.theme.UserIcon
import connects.quotes.ui.theme.QCRadiusButton
import connects.quotes.ui.theme.QCRadiusCard
import connects.quotes.ui.theme.QCRadiusInput

@Composable
fun AuthorScreen(
    currentIdentity: Credentials?,
    authorPassage: Passage?,
    isAuthorAdvertised: Boolean,
    authorSettings: AuthorSettings?,
    inactivityCheckResult: InactivityCheckResult? = null,
    isPerformingCheck: Boolean = false,
    createdPassages: List<Passage>,
    advertisedPassageIds: Set<String> = emptySet(),
    subscribedCount: Int = 0,
    onInspectPassage: (Id) -> Unit,
    onToggleAuthorAdvertise: () -> Unit,
    onTogglePassageAdvertise: (Id) -> Unit = {},
    onBulkSetPassageAdvertisement: (String?, Boolean) -> Unit = { _, _ -> },
    onOpenIdentityDialog: () -> Unit,
    onLogout: () -> Unit,
    onChangePassphrase: (String, String) -> Unit = { _, _ -> },
    onUpdateAuthorSettings: (Int, AuthorDiscardAction) -> Unit = { _, _ -> },
    onSaveProfile: (String?, String?, Boolean) -> Unit = { _, _, _ -> },
    onViewSubscribed: () -> Unit = {},
    onPerformInactivityCheck: () -> Unit = {},
    onDiscardAuthorIdentity: (AuthorDiscardAction?) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var showChangePassDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var usernameInput by remember(authorSettings?.username) { mutableStateOf(authorSettings?.username ?: "") }
    var bioInput by remember(authorSettings?.bio) { mutableStateOf(authorSettings?.bio ?: "") }
    var aliasSearchEnabledInput by remember(authorSettings?.aliasSearchEnabled) { mutableStateOf(authorSettings?.aliasSearchEnabled ?: true) }
    var selectedBulkType by remember { mutableStateOf("ALL") }

    val activePeriodDays = authorSettings?.inactivityPeriodDays ?: 30
    val activeDiscardAction = authorSettings?.discardAction ?: AuthorDiscardAction.ORPHAN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                UserIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "AUTHOR (SOVEREIGN SETTINGS)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (currentIdentity != null) {
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DISCONNECT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (currentIdentity == null) {
            // Anonymous Mode prompt
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(QCRadiusCard)
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
                        text = "No author identity is currently connected. Create a sovereign identity or log in with your UUID and passphrase to view your author settings, inactivity configuration, and manage created thoughts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenIdentityDialog,
                        shape = RoundedCornerShape(QCRadiusButton)
                    ) {
                        Text("SETUP SOVEREIGN IDENTITY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Identity Details Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        text = "[AUTHOR IDENTITY]",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    authorPassage?.let {
                                        Text(
                                            text = "• ${it.content}",
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                authorSettings?.let {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "#${it.inactivityTag}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

                            // Author UUID
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "AUTHOR UUID",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentIdentity.passageId.value,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                OutlinedButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(currentIdentity.passageId.value)) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CopyIcon(size = 10.dp, tint = MaterialTheme.colorScheme.primary)
                                        Text("COPY UUID", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                    }
                                }
                            }

                            // Passphrase
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "SECRET PASSPHRASE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentIdentity.passphrase,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { clipboardManager.setText(AnnotatedString(currentIdentity.passphrase)) },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CopyIcon(size = 10.dp, tint = MaterialTheme.colorScheme.primary)
                                            Text("COPY", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            oldPassInput = currentIdentity.passphrase
                                            newPassInput = ""
                                            showChangePassDialog = true
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("CHANGE", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Author Profile & Public Alias Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AUTHOR PROFILE & PUBLIC ALIAS",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!authorSettings?.username.isNullOrBlank()) {
                                    Text(
                                        text = "@${authorSettings?.username}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Unique Username (@alias for login)", fontSize = 11.sp) },
                                placeholder = { Text("e.g. socrates, hypatia", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = bioInput,
                                onValueChange = { bioInput = it },
                                label = { Text("Author Bio / Thought School", fontSize = 11.sp) },
                                placeholder = { Text("Describe your philosophical principles...", fontSize = 11.sp) },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Allow Alias Discovery & Chat Search",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (aliasSearchEnabledInput)
                                            "Other authors can search and request chats using your @alias or moniker."
                                        else
                                            "Private: Chats can only be initiated using your exact Sovereign UUID.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = aliasSearchEnabledInput,
                                    onCheckedChange = { aliasSearchEnabledInput = it }
                                )
                            }

                            Button(
                                onClick = { onSaveProfile(usernameInput.trim().ifBlank { null }, bioInput.trim().ifBlank { null }, aliasSearchEnabledInput) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("SAVE PROFILE ALIAS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Subscriptions Summary Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "DIRECT SUBSCRIPTIONS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$subscribedCount Following",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            OutlinedButton(
                                onClick = onViewSubscribed,
                                shape = RoundedCornerShape(QCRadiusButton),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("VIEW FEED →", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Inactivity Period Configuration Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "INACTIVITY PERIOD & ACTIVITY TRACKING",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "● ACTIVE",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Every activity session is logged. If your account remains inactive beyond your configured period, all chat messages from your side are deleted, and unadvertised citations are either orphaned or deleted according to your discard action.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick Period Selection Chips
                            Text(
                                text = "SET INACTIVITY THRESHOLD (DAYS):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val periodOptions = listOf(1, 7, 14, 30, 60, 90, 180, 365)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                periodOptions.take(4).forEach { days ->
                                    val isSelected = activePeriodDays == days
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onUpdateAuthorSettings(days, activeDiscardAction) },
                                        label = { Text("${days}d", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                periodOptions.drop(4).forEach { days ->
                                    val isSelected = activePeriodDays == days
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onUpdateAuthorSettings(days, activeDiscardAction) },
                                        label = { Text("${days}d", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Author Discard Action Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "AUTHOR DISCARD ACTION",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "When your account exceeds the inactivity period or if you voluntarily delete your author identity, choose what happens to your unadvertised passages:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Option 1: ORPHAN
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateAuthorSettings(activePeriodDays, AuthorDiscardAction.ORPHAN) }
                                    .border(
                                        if (activeDiscardAction == AuthorDiscardAction.ORPHAN) 1.5.dp else 0.5.dp,
                                        if (activeDiscardAction == AuthorDiscardAction.ORPHAN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(QCRadiusCard)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (activeDiscardAction == AuthorDiscardAction.ORPHAN) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(QCRadiusCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = activeDiscardAction == AuthorDiscardAction.ORPHAN,
                                            onClick = { onUpdateAuthorSettings(activePeriodDays, AuthorDiscardAction.ORPHAN) }
                                        )
                                        Text(
                                            text = "ORPHAN & ADVERTISE ON BILLBOARD (RECOMMENDED)",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "Unadvertised thoughts are preserved as public domain orphan knowledge. Ownership lineage is stripped (parentId = null) and passages become permanently visible on the Billboard.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 36.dp)
                                    )
                                }
                            }

                            // Option 2: DELETE
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateAuthorSettings(activePeriodDays, AuthorDiscardAction.DELETE) }
                                    .border(
                                        if (activeDiscardAction == AuthorDiscardAction.DELETE) 1.5.dp else 0.5.dp,
                                        if (activeDiscardAction == AuthorDiscardAction.DELETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(QCRadiusCard)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (activeDiscardAction == AuthorDiscardAction.DELETE) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(QCRadiusCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = activeDiscardAction == AuthorDiscardAction.DELETE,
                                            onClick = { onUpdateAuthorSettings(activePeriodDays, AuthorDiscardAction.DELETE) }
                                        )
                                        Text(
                                            text = "DELETE PERMANENTLY",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "Unadvertised passages are permanently purged from the server database (ledger-immutable RECORDABLE passages remain preserved as orphans).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 36.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Inactivity Sweep & Identity Discard Actions
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SOVEREIGN PURGE & SWEEP CONTROLS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPerformInactivityCheck,
                                    enabled = !isPerformingCheck,
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isPerformingCheck) "CHECKING..." else "RUN INACTIVITY CHECK",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { showDiscardConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "DELETE AUTHOR IDENTITY",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    }
                }

                // Author Billboard Self-Advertisement Control Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BILLBOARD ADVERTISEMENT",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isAuthorAdvertised) "● ADVERTISED (ON)" else "○ NOT ADVERTISED (OFF)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isAuthorAdvertised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "By default, author profiles are not advertised on the Billboard (default: off). As the owner of your identity passage, you have sovereign control to advertise or unadvertise yourself.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onToggleAuthorAdvertise,
                                shape = RoundedCornerShape(QCRadiusButton),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isAuthorAdvertised) "REMOVE PROFILE FROM BILLBOARD" else "ADVERTISE PROFILE ON BILLBOARD",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Bulk Visibility Controls Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "BULK VISIBILITY & BILLBOARD CONTROLS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Advertise or withdraw entire categories of thoughts to/from the public Billboard simultaneously.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Type Filter Chips
                            Text(
                                text = "SELECT TARGET PASSAGE TYPE:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val typeOptions = listOf("ALL", "QUOTE", "PASSAGE", "COMMENT", "RECORDABLE", "SOURCE")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                typeOptions.take(3).forEach { t ->
                                    FilterChip(
                                        selected = selectedBulkType == t,
                                        onClick = { selectedBulkType = t },
                                        label = { Text(t, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                typeOptions.drop(3).forEach { t ->
                                    FilterChip(
                                        selected = selectedBulkType == t,
                                        onClick = { selectedBulkType = t },
                                        label = { Text(t, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onBulkSetPassageAdvertisement(
                                            if (selectedBulkType == "ALL") null else selectedBulkType,
                                            true
                                        )
                                    },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "📢 ADVERTISE ALL $selectedBulkType",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        onBulkSetPassageAdvertisement(
                                            if (selectedBulkType == "ALL") null else selectedBulkType,
                                            false
                                        )
                                    },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "🔒 UNADVERTISE ALL $selectedBulkType",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Created Passages Section
                item {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CREATED PASSAGES (${createdPassages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All passages authored by your sovereign identity, including your Author passage and published thoughts:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (createdPassages.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(QCRadiusCard)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No passages created yet. Use '+ CREATE' to post your first thought.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(createdPassages, key = { it.id.value }) { passage ->
                        val isAdv = advertisedPassageIds.contains(passage.id.value)
                        val isAuthorNode = passage.id == authorPassage?.id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(QCRadiusCard)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(QCRadiusCard)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            text = "[${passage.type}]",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (passage.isRecordable) {
                                            Text(
                                                text = "• RECORDABLE",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(QCRadiusInput),
                                        color = if (isAdv) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (isAdv) "● ADVERTISED" else "○ PRIVATE",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (isAdv) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = passage.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${passage.subscriberIds.size} ${if (passage.subscriberIds.size == 1) "CITATION" else "CITATIONS"} • ${passage.subscribedToIds.size} REFS",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { onInspectPassage(passage.id) },
                                            shape = RoundedCornerShape(QCRadiusButton),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("INSPECT →", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }

                                        Button(
                                            onClick = {
                                                if (isAuthorNode) {
                                                    onToggleAuthorAdvertise()
                                                } else {
                                                    onTogglePassageAdvertise(passage.id)
                                                }
                                            },
                                            shape = RoundedCornerShape(QCRadiusButton),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isAdv) "REMOVE" else "ADVERTISE",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Change Passphrase Dialog
    if (showChangePassDialog) {
        Dialog(onDismissRequest = { showChangePassDialog = false }) {
            Surface(
                shape = RoundedCornerShape(QCRadiusCard),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.widthIn(max = 420.dp).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "CHANGE SECRET PASSPHRASE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Update your sovereign bearer passphrase. Ensure you store the new passphrase safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = oldPassInput,
                        onValueChange = { oldPassInput = it },
                        label = { Text("CURRENT PASSPHRASE") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it },
                        label = { Text("NEW PASSPHRASE (MIN 8 CHARS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showChangePassDialog = false }) {
                            Text("CANCEL")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (oldPassInput.isNotBlank() && newPassInput.length >= 8) {
                                    onChangePassphrase(oldPassInput.trim(), newPassInput.trim())
                                    showChangePassDialog = false
                                }
                            },
                            enabled = oldPassInput.isNotBlank() && newPassInput.length >= 8
                        ) {
                            Text("UPDATE")
                        }
                    }
                }
            }
        }
    }

    // Discard Author Identity Confirmation Dialog
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = {
                Text(
                    text = "CONFIRM AUTHOR IDENTITY DISCARD",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to discard your sovereign author identity?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• All chats from your side will be permanently deleted.\n• Your unadvertised citations will be ${if (activeDiscardAction == AuthorDiscardAction.ORPHAN) "ORPHANED and placed on the Billboard" else "PERMANENTLY DELETED"}.\n• You will be switched to anonymous mode.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onDiscardAuthorIdentity(activeDiscardAction)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("YES, EXECUTE DISCARD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}
