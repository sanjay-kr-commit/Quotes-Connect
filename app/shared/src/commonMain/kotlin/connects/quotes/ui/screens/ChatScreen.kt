package connects.quotes.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import connects.quotes.model.ChatMessage
import connects.quotes.model.ChatSession
import connects.quotes.model.ChatStatus
import connects.quotes.model.ChatType
import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.ui.theme.*

@Composable
fun ChatScreen(
    currentIdentity: Credentials?,
    chatSessions: List<ChatSession>,
    selectedSession: ChatSession?,
    activeMessages: List<ChatMessage>,
    decryptedMessages: Map<String, String>,
    isLoading: Boolean,
    isLiveConnected: Boolean = false,
    unreadChatIds: Set<String> = emptySet(),
    isKeystoreUnlocked: Boolean = false,
    hasCloudKeystoreBackup: Boolean = false,
    isPeerTyping: Boolean = false,
    typingPeerName: String? = null,
    personalChatNames: Map<String, String> = emptyMap(),
    onSelectSession: (ChatSession?) -> Unit,
    onRequestChat: (recipientId: Id, type: ChatType) -> Unit,
    onRespondChat: (chatId: Id, accept: Boolean) -> Unit,
    onSendMessage: (content: String, citedPassageId: Id?) -> Unit,
    onInspectPassage: (Id) -> Unit,
    onRefresh: () -> Unit,
    onOpenIdentityDialog: () -> Unit,
    onCreateKeystore: (password: String) -> Unit = {},
    onUnlockKeystore: (password: String) -> Unit = {},
    onLockKeystore: () -> Unit = {},
    onResetKeystore: (newPassword: String) -> Unit = {},
    onSetTyping: (chatId: Id, isTyping: Boolean) -> Unit = { _, _ -> },
    onRequestChatResync: (chatId: Id) -> Unit = {},
    onProposeSharedAlias: (chatId: Id, alias: String) -> Unit = { _, _ -> },
    onAcceptSharedAlias: (chatId: Id) -> Unit = {},
    onSetPersonalAlias: (chatId: Id, alias: String?) -> Unit = { _, _ -> },
    onDeleteChat: (chatId: Id) -> Unit = {},
    suggestedAuthors: List<String> = emptyList()
) {
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showCreateKeystoreDialog by remember { mutableStateOf(false) }
    var showUnlockKeystoreDialog by remember { mutableStateOf(false) }
    var showResetKeystoreDialog by remember { mutableStateOf(false) }

    if (currentIdentity == null) {
        // Identity Required Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChatIcon(size = 36.dp, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "SOVEREIGN IDENTITY REQUIRED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Personal chats and private citation exchanges require an Author identity. Keys for asymmetric end-to-end encryption are generated and kept exclusively on your device.",
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
        }
        return
    }

    val authorId = currentIdentity.passageId

    if (selectedSession != null) {
        // Active Conversation View
        ChatConversationView(
            authorId = authorId,
            session = selectedSession,
            messages = activeMessages,
            decryptedMessages = decryptedMessages,
            isLiveConnected = isLiveConnected,
            isKeystoreUnlocked = isKeystoreUnlocked,
            isPeerTyping = isPeerTyping,
            typingPeerName = typingPeerName,
            personalName = personalChatNames[selectedSession.id.value],
            onBack = { onSelectSession(null) },
            onSendMessage = onSendMessage,
            onInspectPassage = onInspectPassage,
            onRefresh = onRefresh,
            onOpenUnlockKeystore = { showUnlockKeystoreDialog = true },
            onSetTyping = { isTyping -> onSetTyping(selectedSession.id, isTyping) },
            onRequestChatResync = { onRequestChatResync(selectedSession.id) },
            onProposeSharedAlias = onProposeSharedAlias,
            onAcceptSharedAlias = onAcceptSharedAlias,
            onSetPersonalAlias = { alias -> onSetPersonalAlias(selectedSession.id, alias) },
            onDeleteChat = { onDeleteChat(selectedSession.id) }
        )
    } else {
        // Chat Sessions List View
        ChatSessionsListView(
            authorId = authorId,
            chatSessions = chatSessions,
            unreadChatIds = unreadChatIds,
            isLoading = isLoading,
            isKeystoreUnlocked = isKeystoreUnlocked,
            hasCloudKeystoreBackup = hasCloudKeystoreBackup,
            personalChatNames = personalChatNames,
            onSelectSession = onSelectSession,
            onOpenNewChat = { showNewChatDialog = true },
            onRespondChat = onRespondChat,
            onRefresh = onRefresh,
            onOpenCreateKeystore = { showCreateKeystoreDialog = true },
            onOpenUnlockKeystore = { showUnlockKeystoreDialog = true },
            onLockKeystore = onLockKeystore,
            onOpenResetKeystore = { showResetKeystoreDialog = true },
            onDeleteChat = onDeleteChat
        )
    }

    if (showNewChatDialog) {
        NewChatDialog(
            isKeystoreUnlocked = isKeystoreUnlocked,
            hasCloudKeystoreBackup = hasCloudKeystoreBackup,
            suggestedAuthors = suggestedAuthors,
            onOpenCreateKeystore = { showCreateKeystoreDialog = true },
            onOpenUnlockKeystore = { showUnlockKeystoreDialog = true },
            onDismiss = { showNewChatDialog = false },
            onSubmit = { recipientId, type ->
                onRequestChat(recipientId, type)
                showNewChatDialog = false
            }
        )
    }

    if (showCreateKeystoreDialog) {
        CreateKeystoreDialog(
            onDismiss = { showCreateKeystoreDialog = false },
            onSubmit = { password ->
                onCreateKeystore(password)
                showCreateKeystoreDialog = false
            }
        )
    }

    if (showUnlockKeystoreDialog) {
        UnlockKeystoreDialog(
            onDismiss = { showUnlockKeystoreDialog = false },
            onSubmit = { password ->
                onUnlockKeystore(password)
                showUnlockKeystoreDialog = false
            }
        )
    }

    if (showResetKeystoreDialog) {
        ResetKeystoreDialog(
            onDismiss = { showResetKeystoreDialog = false },
            onSubmit = { newPassword ->
                onResetKeystore(newPassword)
                showResetKeystoreDialog = false
            }
        )
    }
}

@Composable
private fun ChatSessionsListView(
    authorId: Id,
    chatSessions: List<ChatSession>,
    unreadChatIds: Set<String>,
    isLoading: Boolean,
    isKeystoreUnlocked: Boolean,
    hasCloudKeystoreBackup: Boolean,
    personalChatNames: Map<String, String>,
    onSelectSession: (ChatSession) -> Unit,
    onOpenNewChat: () -> Unit,
    onRespondChat: (chatId: Id, accept: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenCreateKeystore: () -> Unit,
    onOpenUnlockKeystore: () -> Unit,
    onLockKeystore: () -> Unit = {},
    onOpenResetKeystore: () -> Unit,
    onDeleteChat: (Id) -> Unit
) {
    val pendingIncoming = chatSessions.filter { it.recipientId == authorId && it.status == ChatStatus.PENDING }
    val pendingOutgoing = chatSessions.filter { it.initiatorId == authorId && it.status == ChatStatus.PENDING }
    val activeChats = chatSessions.filter { it.status == ChatStatus.ACCEPTED }
    var searchQuery by remember { mutableStateOf("") }

    val filteredActiveChats = if (searchQuery.isBlank()) {
        activeChats
    } else {
        val q = searchQuery.trim().lowercase()
        activeChats.filter { s ->
            val counterparty = if (s.initiatorId == authorId) s.recipientId else s.initiatorId
            val personal = personalChatNames[s.id.value]
            counterparty.value.lowercase().contains(q) ||
                (personal?.lowercase()?.contains(q) == true) ||
                (s.sharedAlias?.lowercase()?.contains(q) == true) ||
                s.id.value.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Header (Responsive for mobile & desktop)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                if (maxWidth < 560.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "PERSONAL CITATIONS & CHATS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onRefresh,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isLoading) "SYNCING..." else "REFRESH",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = onOpenNewChat,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AddIcon(size = 14.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                    Text(
                                        text = "START CHAT",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatIcon(size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "PERSONAL CITATIONS & CHATS",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRefresh,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isLoading) "SYNCING..." else "REFRESH",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = onOpenNewChat,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AddIcon(size = 14.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                    Text(
                                        text = "START CHAT",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sovereign Keystore Status & Backup Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isKeystoreUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else if (hasCloudKeystoreBackup) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp)
                    ),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isKeystoreUnlocked) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                text = if (isKeystoreUnlocked) "🔓" else if (hasCloudKeystoreBackup) "🔒" else "⚠️",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "SOVEREIGN KEYSTORE: " + when {
                                    isKeystoreUnlocked -> "ACTIVE & UNLOCKED"
                                    hasCloudKeystoreBackup -> "LOCKED (CLOUD BACKUP)"
                                    else -> "NOT INITIALIZED"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isKeystoreUnlocked) MaterialTheme.colorScheme.primary
                                else if (hasCloudKeystoreBackup) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isKeystoreUnlocked) {
                                OutlinedButton(
                                    onClick = onLockKeystore,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("LOCK", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!isKeystoreUnlocked && hasCloudKeystoreBackup) {
                                Button(
                                    onClick = onOpenUnlockKeystore,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("UNLOCK", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasCloudKeystoreBackup && !isKeystoreUnlocked) {
                                Button(
                                    onClick = onOpenCreateKeystore,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("CREATE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenResetKeystore,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("RESET", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }

                    Text(
                        text = when {
                            isKeystoreUnlocked -> "Asymmetric key pairs are unlocked and active in client memory. Encrypted chats can be sent, decrypted, and synchronized with cloud storage."
                            hasCloudKeystoreBackup -> "An encrypted keystore backup exists on the server. Decrypt it with your password to view private citations and send messages in encrypted channels."
                            else -> "No sovereign keystore initialized yet. Create a password-protected keystore to securely backup keys to the server and unlock encrypted citation chats."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Info Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PRIVACY & ENCRYPTION ARCHITECTURE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• ☁️ Cloud Encrypted Chats: Ciphertext persisted in server database. Keys encrypted with your password via PBKDF2 + AES-GCM and stored on server.\n• 🔒 Sovereign Ephemeral Chats: Zero server database storage. Ephemeral in-memory relay mailbox only.\n• 💬 Unencrypted Chats: Plaintext citation messages stored in server database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pending Incoming Requests
        if (pendingIncoming.isNotEmpty()) {
            item {
                Text(
                    text = "PENDING INCOMING REQUESTS (${pendingIncoming.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(pendingIncoming) { session ->
                PendingIncomingCard(
                    session = session,
                    isKeystoreUnlocked = isKeystoreUnlocked,
                    hasCloudKeystoreBackup = hasCloudKeystoreBackup,
                    onOpenCreateKeystore = onOpenCreateKeystore,
                    onOpenUnlockKeystore = onOpenUnlockKeystore,
                    onAccept = { onRespondChat(session.id, true) },
                    onDecline = { onRespondChat(session.id, false) }
                )
            }
        }

        // Pending Outgoing Requests
        if (pendingOutgoing.isNotEmpty()) {
            item {
                Text(
                    text = "AWAITING RECIPIENT ACCEPTANCE (${pendingOutgoing.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(pendingOutgoing) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "To: ${session.recipientId.value.take(12)}...",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            ChatTypeBadge(type = session.type)
                        }
                        Text(
                            text = "PENDING ACCEPT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Toolbar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("🔍 Search conversations by peer moniker, alias, or ID...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )
        }

        // Active Chats Header
        item {
            Text(
                text = "ACTIVE CONVERSATIONS (${filteredActiveChats.size})",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filteredActiveChats.isEmpty() && pendingIncoming.isEmpty() && pendingOutgoing.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChatIcon(size = 32.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (searchQuery.isNotBlank()) "NO CONVERSATIONS MATCH SEARCH" else "NO CONVERSATIONS YET",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try a different search term or clear the filter." else "Click 'START CHAT' to initiate private citation exchanges with any author.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredActiveChats) { session ->
                val counterpartyId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
                val isUnread = unreadChatIds.contains(session.id.value)
                ActiveChatCard(
                    session = session,
                    counterpartyId = counterpartyId,
                    isUnread = isUnread,
                    personalName = personalChatNames[session.id.value],
                    onDelete = { onDeleteChat(session.id) },
                    onClick = { onSelectSession(session) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PendingIncomingCard(
    session: ChatSession,
    isKeystoreUnlocked: Boolean = true,
    hasCloudKeystoreBackup: Boolean = false,
    onOpenCreateKeystore: () -> Unit = {},
    onOpenUnlockKeystore: () -> Unit = {},
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var showMissingKeystoreAlert by remember { mutableStateOf(false) }

    if (showMissingKeystoreAlert) {
        AlertDialog(
            onDismissRequest = { showMissingKeystoreAlert = false },
            title = {
                Text(
                    text = if (hasCloudKeystoreBackup) "DECRYPT KEYSTORE REQUIRED" else "KEYSTORE REQUIRED",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            },
            text = {
                Text(
                    text = if (hasCloudKeystoreBackup)
                        "A cloud keystore backup was found. Please decrypt it before accepting this encrypted conversation."
                    else
                        "You don't have a keystore to continue this conversation. Please create a keystore with key phrase to continue or start this conversation.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingKeystoreAlert = false
                        if (hasCloudKeystoreBackup) onOpenUnlockKeystore() else onOpenCreateKeystore()
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(if (hasCloudKeystoreBackup) "DECRYPT NOW" else "CREATE KEYSTORE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMissingKeystoreAlert = false }, shape = RoundedCornerShape(4.dp)) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    val isEncrypted = session.type == ChatType.ENCRYPTED || session.type == ChatType.ENCRYPTED_CLOUD

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Chat Request from Author:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.initiatorId.value,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                ChatTypeBadge(type = session.type)
            }

            Text(
                text = when (session.type) {
                    ChatType.ENCRYPTED -> "🔒 Sovereign ephemeral relay. Private keys remain strictly on your device."
                    ChatType.ENCRYPTED_CLOUD -> "☁️ Cloud-backed encrypted chat. Ciphertext stored in cloud database. Zero server plaintext access."
                    ChatType.UNENCRYPTED -> "💬 Plaintext conversation will be recorded on the server database."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("DECLINE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        if (isEncrypted && !isKeystoreUnlocked) {
                            showMissingKeystoreAlert = true
                        } else {
                            onAccept()
                        }
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ACCEPT & CONNECT", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ActiveChatCard(
    session: ChatSession,
    counterpartyId: Id,
    isUnread: Boolean,
    personalName: String?,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "DELETE CONVERSATION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this chat session? All associated messages and local records will be removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    val displayName = personalName ?: session.sharedAlias ?: counterpartyId.value

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (personalName != null) {
                        Text(
                            text = "[Personal]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (session.sharedAlias != null) {
                        Text(
                            text = "[Shared]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isUnread) {
                        Text(
                            text = "● NEW",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "ID: ${session.id.value.take(16)}... • Peer: ${counterpartyId.value.take(10)}...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatTypeBadge(type = session.type)
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = "OPEN →",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatConversationView(
    authorId: Id,
    session: ChatSession,
    messages: List<ChatMessage>,
    decryptedMessages: Map<String, String>,
    isLiveConnected: Boolean,
    isKeystoreUnlocked: Boolean,
    isPeerTyping: Boolean,
    typingPeerName: String?,
    personalName: String?,
    onBack: () -> Unit,
    onSendMessage: (content: String, citedPassageId: Id?) -> Unit,
    onInspectPassage: (Id) -> Unit,
    onRefresh: () -> Unit,
    onOpenUnlockKeystore: () -> Unit,
    onSetTyping: (Boolean) -> Unit,
    onRequestChatResync: () -> Unit,
    onProposeSharedAlias: (chatId: Id, alias: String) -> Unit = { _, _ -> },
    onAcceptSharedAlias: (chatId: Id) -> Unit = {},
    onSetPersonalAlias: (String?) -> Unit = {},
    onDeleteChat: () -> Unit = {}
) {
    val counterpartyId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
    val isEncryptedChat = session.type == ChatType.ENCRYPTED || session.type == ChatType.ENCRYPTED_CLOUD
    val isLocked = isEncryptedChat && !isKeystoreUnlocked
    var messageText by remember { mutableStateOf("") }
    var citedPassageInput by remember { mutableStateOf("") }
    var showCitationInput by remember { mutableStateOf(false) }

    var showPersonalAliasDialog by remember { mutableStateOf(false) }
    var personalAliasInput by remember { mutableStateOf("") }
    var showProposeAliasDialog by remember { mutableStateOf(false) }
    var proposeAliasInput by remember { mutableStateOf("") }
    var dismissedProposal by remember(session.proposedSharedAlias) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Conversation Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onSetTyping(false)
                        onBack()
                    },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("← BACK", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = counterpartyId.value.take(16) + if (counterpartyId.value.length > 16) "..." else "",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (isLiveConnected) {
                            Text(
                                text = "🟢 LIVE (TCP)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "○ DISCONNECTED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = when (session.type) {
                            ChatType.ENCRYPTED -> "🔒 Ephemeral E2EE Mailbox (Persistent on Device)"
                            ChatType.ENCRYPTED_CLOUD -> "☁️ Cloud-Backed E2EE (Encrypted in Cloud, Client Keys)"
                            ChatType.UNENCRYPTED -> "💬 Unencrypted Server Chat"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (isEncryptedChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isEncryptedChat) {
                    OutlinedButton(
                        onClick = onRequestChatResync,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🔄 RESYNC", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("REFRESH", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Alias Management Bar
        val effectivePersonalName = personalName
        val displayName = effectivePersonalName ?: session.sharedAlias ?: counterpartyId.value
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CHAT WITH: ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayName.take(20),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    if (effectivePersonalName != null) {
                        Text(
                            text = "Personal Name: \"$effectivePersonalName\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.sharedAlias != null) {
                        Text(
                            text = "Shared Name: \"${session.sharedAlias}\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            personalAliasInput = effectivePersonalName ?: ""
                            showPersonalAliasDialog = true
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("🏷️ Personal", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            proposeAliasInput = session.sharedAlias ?: ""
                            showProposeAliasDialog = true
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("🤝 Shared", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }

        // Proposed Shared Alias Banner
        if (!dismissedProposal && session.proposedSharedAlias != null && session.aliasProposerId != null && session.aliasProposerId != authorId) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🤝 Peer proposed shared name: \"${session.proposedSharedAlias}\"",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onAcceptSharedAlias(session.id) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("ACCEPT", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { dismissedProposal = true },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("DISMISS", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Locked Keystore Warning Banner
        if (isLocked) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔒 SOVEREIGN KEYSTORE LOCKED",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Encrypted messages are visible as ciphertext references. Decrypt keystore with your password to view citations and send messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenUnlockKeystore,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("UNLOCK", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Messages Thread
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (session.type) {
                                ChatType.ENCRYPTED -> "Ephemeral encrypted channel ready. Send a private citation or message."
                                ChatType.ENCRYPTED_CLOUD -> "Cloud-backed encrypted channel ready. Messages are persisted securely in cloud database."
                                ChatType.UNENCRYPTED -> "Unencrypted channel ready. Messages will be stored in server database."
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(messages) { msg ->
                    val isMe = msg.senderId == authorId
                    val displayContent = decryptedMessages[msg.id.value] ?: msg.content

                    ChatMessageBubble(
                        message = msg,
                        displayContent = displayContent,
                        isMe = isMe,
                        onInspectPassage = onInspectPassage
                    )
                }
            }
        }

        // Live Quoting Indicator Animation
        if (isPeerTyping) {
            QuotingIndicator(peerName = typingPeerName)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Citation input toggle
        if (showCitationInput) {
            OutlinedTextField(
                value = citedPassageInput,
                onValueChange = { citedPassageInput = it },
                label = { Text("Cited Passage ID (Optional Private Citation)") },
                placeholder = { Text("e.g. pas-1234...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )
        }

        // Message Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showCitationInput = !showCitationInput },
                enabled = !isLocked,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (showCitationInput) "HIDE CITE" else "+ CITE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                    onSetTyping(it.isNotBlank())
                },
                enabled = !isLocked,
                placeholder = {
                    Text(
                        when {
                            isLocked -> "🔒 Unlock keystore to send quotes/messages..."
                            isEncryptedChat -> "Type encrypted message..."
                            else -> "Type unencrypted message..."
                        },
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                shape = RoundedCornerShape(4.dp)
            )

            Button(
                onClick = {
                    val citedId = if (citedPassageInput.isNotBlank()) Id(citedPassageInput.trim()) else null
                    onSendMessage(messageText, citedId)
                    messageText = ""
                    citedPassageInput = ""
                    showCitationInput = false
                    onSetTyping(false)
                },
                enabled = messageText.isNotBlank() && !isLocked,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("SEND", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPersonalAliasDialog) {
        AlertDialog(
            onDismissRequest = { showPersonalAliasDialog = false },
            title = {
                Text("SET PERSONAL NAME", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set a personal name for this chat. Only stored and visible on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = personalAliasInput,
                        onValueChange = { personalAliasInput = it },
                        label = { Text("Personal Name") },
                        placeholder = { Text("e.g. Mentor Bob, Socrates") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val alias = personalAliasInput.trim().ifEmpty { null }
                        onSetPersonalAlias(alias)
                        showPersonalAliasDialog = false
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("SAVE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onSetPersonalAlias(null)
                        personalAliasInput = ""
                        showPersonalAliasDialog = false
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CLEAR", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("DELETE CHAT CONVERSATION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete this chat session? All messages and local records will be deleted from your device and the server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onSetTyping(false)
                        onDeleteChat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    if (showProposeAliasDialog) {
        AlertDialog(
            onDismissRequest = { showProposeAliasDialog = false },
            title = {
                Text("PROPOSE SHARED NAME", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Propose a shared conversation name. Both authors will see it once accepted by peer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = proposeAliasInput,
                        onValueChange = { proposeAliasInput = it },
                        label = { Text("Shared Name Proposal") },
                        placeholder = { Text("e.g. Philosophy Dialogue #1") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (proposeAliasInput.isNotBlank()) {
                            onProposeSharedAlias(session.id, proposeAliasInput.trim())
                            showProposeAliasDialog = false
                        }
                    },
                    enabled = proposeAliasInput.isNotBlank(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("PROPOSE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showProposeAliasDialog = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
private fun QuotingIndicator(peerName: String?) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "❝",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
        Text(
            text = if (!peerName.isNullOrBlank()) "$peerName is quoting..." else "quoting...",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
        Text(
            text = "❞",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    displayContent: String,
    isMe: Boolean,
    onInspectPassage: (Id) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
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
                        text = if (isMe) "YOU" else message.senderId.value.take(8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (message.isEncrypted) "🔒 ENCRYPTED" else "💬 UNENCRYPTED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = if (message.isEncrypted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If citation attached
                val citedId = message.citedPassageId
                if (citedId != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectPassage(citedId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📌", fontSize = 11.sp)
                            Text(
                                text = "Cited Passage: ${citedId.value.take(14)}...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (displayContent.startsWith("[🔒")) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒", fontSize = 12.sp)
                        Text(
                            text = displayContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTypeBadge(type: ChatType) {
    val (label, containerColor, textColor) = when (type) {
        ChatType.ENCRYPTED -> Triple("🔒 EPHEMERAL E2EE", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ChatType.ENCRYPTED_CLOUD -> Triple("☁️ CLOUD E2EE", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        ChatType.UNENCRYPTED -> Triple("💬 UNENCRYPTED", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NewChatDialog(
    isKeystoreUnlocked: Boolean,
    hasCloudKeystoreBackup: Boolean,
    suggestedAuthors: List<String> = emptyList(),
    onOpenCreateKeystore: () -> Unit,
    onOpenUnlockKeystore: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (recipientId: Id, type: ChatType) -> Unit
) {
    var recipientIdInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChatType.UNENCRYPTED) }

    val requiresKeystoreAction = (selectedType == ChatType.ENCRYPTED || selectedType == ChatType.ENCRYPTED_CLOUD) && !isKeystoreUnlocked

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "START NEW CHAT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Initiate a personal chat session with another author in the graph.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = recipientIdInput,
                    onValueChange = { recipientIdInput = it },
                    label = { Text("Target Author ID / Passage ID or @alias") },
                    placeholder = { Text("e.g. pas-... or @socrates") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                if (suggestedAuthors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "SUGGESTED / SUBSCRIBED AUTHORS:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestedAuthors.take(4).forEach { authorIdStr ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { recipientIdInput = authorIdStr }
                                ) {
                                    Text(
                                        text = "★ ${authorIdStr.take(10)}...",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SELECT CHAT CHANNEL TYPE:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Button 1: Unencrypted Cloud
                        if (selectedType == ChatType.UNENCRYPTED) {
                            Button(
                                onClick = { selectedType = ChatType.UNENCRYPTED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Unencrypted Cloud", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedType = ChatType.UNENCRYPTED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Unencrypted Cloud", fontSize = 10.sp, maxLines = 1)
                            }
                        }

                        // Button 2: Encrypted On-Device
                        if (selectedType == ChatType.ENCRYPTED) {
                            Button(
                                onClick = { selectedType = ChatType.ENCRYPTED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Encrypted On-Device", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedType = ChatType.ENCRYPTED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Encrypted On-Device", fontSize = 10.sp, maxLines = 1)
                            }
                        }

                        // Button 3: Encrypted Cloud (Only visible if keystore unlocked or cloud keystore exists)
                        if (isKeystoreUnlocked || hasCloudKeystoreBackup) {
                            if (selectedType == ChatType.ENCRYPTED_CLOUD) {
                                Button(
                                    onClick = { selectedType = ChatType.ENCRYPTED_CLOUD },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Encrypted Cloud", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { selectedType = ChatType.ENCRYPTED_CLOUD },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Encrypted Cloud", fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    Text(
                        text = when (selectedType) {
                            ChatType.ENCRYPTED_CLOUD -> "☁️ Encrypted Cloud: Ciphertext stored in cloud database. Client manages sovereign keys backed up to cloud with symmetric password encryption."
                            ChatType.ENCRYPTED -> "🔒 Encrypted On-Device: Ephemeral channel. Asymmetric keys held strictly on client device; zero server database storage."
                            ChatType.UNENCRYPTED -> "💬 Unencrypted Cloud: Standard plaintext chat stored directly in server database for persistent history."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Keystore Gating Warning inside Dialog
                if (requiresKeystoreAction) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (hasCloudKeystoreBackup) "⚠️ DECRYPT KEYSTORE REQUIRED" else "⚠️ KEYSTORE SETUP REQUIRED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (hasCloudKeystoreBackup)
                                    "A cloud keystore backup was found for your identity. Decrypt it before starting an encrypted chat."
                                else
                                    "You don't have a keystore to continue this conversation. Please create a keystore with key phrase to continue or start this conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasCloudKeystoreBackup) {
                                Button(
                                    onClick = onOpenUnlockKeystore,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("DECRYPT KEYSTORE NOW", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onOpenCreateKeystore,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("CREATE KEYSTORE NOW", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientIdInput.isNotBlank()) {
                        onSubmit(Id(recipientIdInput.trim()), selectedType)
                    }
                },
                enabled = recipientIdInput.isNotBlank() && !requiresKeystoreAction,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("SEND CHAT REQUEST", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    )
}

@Composable
private fun CreateKeystoreDialog(
    onDismiss: () -> Unit,
    onSubmit: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "CREATE SOVEREIGN KEYSTORE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "A new RSA-2048 key pair will be generated on your device. The private key will be encrypted symmetrically using AES-256-GCM derived from your password (PBKDF2) and securely backed up to the cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Keystore Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm Keystore Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length < 6) {
                        error = "Password must be at least 6 characters."
                    } else if (password != confirmPassword) {
                        error = "Passwords do not match."
                    } else {
                        onSubmit(password)
                    }
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("CREATE & BACKUP", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    )
}

@Composable
private fun UnlockKeystoreDialog(
    onDismiss: () -> Unit,
    onSubmit: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "UNLOCK SOVEREIGN KEYSTORE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your keystore password to download and decrypt your private keys. This will unlock citation decryption and message sending in all encrypted chats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Keystore Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotBlank()) {
                        onSubmit(password)
                    }
                },
                enabled = password.isNotBlank(),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("DECRYPT & UNLOCK", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    )
}

@Composable
private fun ResetKeystoreDialog(
    onDismiss: () -> Unit,
    onSubmit: (newPassword: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "RESET SOVEREIGN KEYSTORE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "⚠️ Resetting your keystore will wipe existing cloud and device keys. Fresh asymmetric keys will be generated, encrypted with your new password, and pushed to cloud. Your new public key will be sent to all existing encrypted chats so you can continue conversations and request peers to resynchronize chat history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("New Keystore Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.length < 6) {
                        error = "Password must be at least 6 characters."
                    } else if (newPassword != confirmPassword) {
                        error = "Passwords do not match."
                    } else {
                        onSubmit(newPassword)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RESET & ROTATE KEYS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    )
}

