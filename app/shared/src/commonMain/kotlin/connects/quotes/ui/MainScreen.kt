package connects.quotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import connects.quotes.model.Id
import connects.quotes.ui.components.CreatePassageDialog
import connects.quotes.ui.components.IdentityDialog
import connects.quotes.ui.components.PassageCard
import connects.quotes.ui.screens.BillboardScreen
import connects.quotes.ui.screens.FeedScreen
import connects.quotes.ui.screens.ChatScreen
import connects.quotes.ui.screens.NotificationsScreen
import connects.quotes.ui.screens.PassageDetailScreen
import connects.quotes.ui.screens.AuthorScreen
import connects.quotes.ui.screens.ProfileScreen
import connects.quotes.ui.theme.*
import connects.quotes.viewmodel.QuotesViewModel

enum class MainTab {
    BILLBOARD,
    FEED,
    SUBSCRIBED,
    NOTIFICATIONS,
    CHAT,
    AUTHOR
}

@Composable
fun MainScreen(viewModel: QuotesViewModel = remember { QuotesViewModel() }) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createTargetId by remember { mutableStateOf<Id?>(null) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Brand, Controls & Actions (Responsive for Android & Desktop)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompact = maxWidth < 600.dp

                    if (isCompact) {
                        // Compact / Android Mobile layout: 2 clean rows
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Brand title & CREATE button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "QUOTES CONNECT",
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ALGORITHM-FREE KNOWLEDGE GRAPH",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        createTargetId = null
                                        showCreateDialog = true
                                    },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AddIcon(size = 14.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                        Text(
                                            text = "CREATE",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Row 2: Status, Security & Identity Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel Security Chip
                                connects.quotes.ui.components.ChannelSecurityChip(
                                    mode = uiState.channelSecurityMode,
                                    onClick = { showEndpointDialog = true }
                                )

                                // Server Endpoint / Status Pill
                                Row(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { showEndpointDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isServerAvailable) "●" else "○",
                                        color = if (uiState.isServerAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = uiState.serverEndpoint.host.take(10),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }

                                // Sovereign Identity Badge
                                Row(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { showIdentityDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserIcon(size = 12.dp, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = uiState.currentIdentity?.passageId?.value?.take(8) ?: "ANON",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        // Desktop / Tablet / Wide layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "QUOTES CONNECT",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ALGORITHM-FREE KNOWLEDGE GRAPH",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel Security Chip (SSL/TLS vs Asymmetric Encrypted Channel)
                                connects.quotes.ui.components.ChannelSecurityChip(
                                    mode = uiState.channelSecurityMode,
                                    onClick = { showEndpointDialog = true }
                                )

                                // Server Endpoint / Status Pill
                                Row(
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { showEndpointDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isServerAvailable) "●" else "○",
                                        color = if (uiState.isServerAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = uiState.serverEndpoint.host.take(12),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Sovereign Identity Badge
                                Row(
                                    modifier = Modifier
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { showIdentityDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserIcon(size = 14.dp, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = uiState.currentIdentity?.passageId?.value?.take(6) ?: "ANONYMOUS",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Universal Create Button
                                Button(
                                    onClick = {
                                        createTargetId = null
                                        showCreateDialog = true
                                    },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AddIcon(size = 14.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                        Text(
                                            text = "CREATE",
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

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearch(it) },
                    placeholder = {
                        Text(
                            "Search passages, thoughts, authors, or #tags...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs (Horizontally scrollable for all screen widths)
                val tabScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabScrollState),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        MainTab.BILLBOARD to "BILLBOARD",
                        MainTab.FEED to "FEED",
                        MainTab.SUBSCRIBED to "SUBSCRIBED",
                        MainTab.NOTIFICATIONS to "NOTIFICATIONS",
                        MainTab.CHAT to if (uiState.unreadChatIds.isNotEmpty()) "CHAT (${uiState.unreadChatIds.size})" else "CHAT",
                        MainTab.AUTHOR to "AUTHOR"
                    ).forEach { (tab, label) ->
                        val isSelected = uiState.activeTab == tab && uiState.inspectedPassage == null && uiState.searchResults == null
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (tab == MainTab.CHAT && uiState.unreadChatIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    viewModel.setTab(tab)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                // Real-time Chat Notification Alert Banner (When user is not in that chat)
                if (uiState.chatNotificationAlert != null) {
                    val alert = uiState.chatNotificationAlert!!
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(QCRadiusCard))
                            .clickable { viewModel.openChatFromNotification(alert.chatId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(QCRadiusCard)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💬", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = "NEW MESSAGE FROM ${alert.senderId.value.take(12)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = alert.text,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { viewModel.openChatFromNotification(alert.chatId) },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                TextButton(
                                    onClick = { viewModel.dismissChatNotification() },
                                    shape = RoundedCornerShape(QCRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("✕", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Server Unavailability Persistent Banner
                if (!uiState.isServerAvailable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    connects.quotes.ui.components.ServerStatusBanner(
                        endpoint = uiState.serverEndpoint,
                        isChecking = uiState.isCheckingConnection,
                        onConfigure = { showEndpointDialog = true },
                        onRetry = { viewModel.retryConnection() }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Search Results Overlay
                uiState.searchResults != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEARCH RESULTS (${uiState.searchResults!!.size})",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "CLEAR",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.onSearch("") }
                            )
                        }

                        if (uiState.searchResults!!.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No matching passages found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.searchResults!!, key = { it.id.value }) { passage ->
                                    PassageCard(
                                        passage = passage,
                                        onInspect = { viewModel.inspectPassage(passage.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Inspected Passage View
                uiState.inspectedPassage != null -> {
                    PassageDetailScreen(
                        passage = uiState.inspectedPassage!!,
                        subscribers = uiState.inspectedSubscribers,
                        subscribedTo = uiState.inspectedSubscribedTo,
                        treeItems = uiState.inspectedTreeItems,
                        isOrphan = uiState.inspectedIsOrphan,
                        isAdvertised = uiState.inspectedIsAdvertised,
                        navigationStack = uiState.navigationStack,
                        onBack = { viewModel.popInspection() },
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onToggleAdvertise = { viewModel.toggleAdvertisement(uiState.inspectedPassage!!.id) },
                        onReplyOrSubscribe = { targetId ->
                            createTargetId = targetId
                            showCreateDialog = true
                        }
                    )
                }

                // Tab 1: Billboard
                uiState.activeTab == MainTab.BILLBOARD -> {
                    BillboardScreen(
                        items = uiState.billboardItems,
                        topTags = uiState.topTags,
                        selectedTag = uiState.selectedTag,
                        onSelectTag = { viewModel.selectTag(it) },
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onToggleAdvertise = { viewModel.toggleAdvertisement(it) }
                    )
                }

                // Tab 2: Feed
                uiState.activeTab == MainTab.FEED -> {
                    FeedScreen(
                        feedItems = uiState.feedItems,
                        feedDepth = uiState.feedDepth,
                        onDepthChange = { viewModel.setFeedDepth(it) },
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onOpenIdentityDialog = { showIdentityDialog = true }
                    )
                }

                // Tab 3: Subscribed
                uiState.activeTab == MainTab.SUBSCRIBED -> {
                    connects.quotes.ui.screens.SubscribedScreen(
                        subscribedItems = uiState.subscribedItems,
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onToggleSubscribe = { viewModel.toggleDirectSubscription(it) },
                        onOpenIdentityDialog = { showIdentityDialog = true }
                    )
                }

                // Tab 4: Notifications
                uiState.activeTab == MainTab.NOTIFICATIONS -> {
                    NotificationsScreen(
                        notifications = uiState.notifications,
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onOpenIdentityDialog = { showIdentityDialog = true }
                    )
                }

                // Tab 5: Chat
                uiState.activeTab == MainTab.CHAT -> {
                    ChatScreen(
                        currentIdentity = uiState.currentIdentity,
                        chatSessions = uiState.chatSessions,
                        selectedSession = uiState.selectedChatSession,
                        activeMessages = uiState.activeChatMessages,
                        decryptedMessages = uiState.decryptedMessages,
                        isLoading = uiState.isChatLoading,
                        isLiveConnected = uiState.isLiveChatConnected,
                        unreadChatIds = uiState.unreadChatIds,
                        isKeystoreUnlocked = uiState.isKeystoreUnlocked,
                        hasCloudKeystoreBackup = uiState.hasCloudKeystoreBackup,
                        isPeerTyping = uiState.isPeerTyping,
                        typingPeerName = uiState.typingPeerName,
                        personalChatNames = uiState.personalChatNames,
                        onSelectSession = { viewModel.selectChatSession(it) },
                        onRequestChat = { recipientId, type -> viewModel.requestChat(recipientId.value, type) },
                        onRespondChat = { chatId, accept -> viewModel.respondChat(chatId, accept) },
                        onSendMessage = { content, citedPassageId -> viewModel.sendChatMessage(content, citedPassageId) },
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onRefresh = { viewModel.refreshChats() },
                        onOpenIdentityDialog = { showIdentityDialog = true },
                        onCreateKeystore = { password -> viewModel.createAndBackupKeystore(password) },
                        onUnlockKeystore = { password -> viewModel.unlockKeystore(password) },
                        onLockKeystore = { viewModel.lockKeystore() },
                        onResetKeystore = { newPassword -> viewModel.resetKeystore(newPassword) },
                        onSetTyping = { chatId, isTyping -> viewModel.setTyping(chatId, isTyping) },
                        onRequestChatResync = { chatId -> viewModel.requestChatResync(chatId) },
                        onProposeSharedAlias = { chatId, alias -> viewModel.proposeChatAlias(chatId, alias) },
                        onAcceptSharedAlias = { chatId -> viewModel.acceptChatAlias(chatId) },
                        onSetPersonalAlias = { chatId, alias -> viewModel.setPersonalAlias(chatId, alias) },
                        onDeleteChat = { chatId -> viewModel.deleteChat(chatId) },
                        suggestedAuthors = uiState.subscribedPassageIds.toList()
                    )
                }

                // Tab 6: Author
                uiState.activeTab == MainTab.AUTHOR -> {
                    AuthorScreen(
                        currentIdentity = uiState.currentIdentity,
                        authorPassage = uiState.authorPassage,
                        isAuthorAdvertised = uiState.isAuthorAdvertised,
                        authorSettings = uiState.authorSettings,
                        inactivityCheckResult = uiState.inactivityCheckResult,
                        isPerformingCheck = uiState.isPerformingInactivityCheck,
                        createdPassages = uiState.createdPassages,
                        advertisedPassageIds = uiState.advertisedPassageIds,
                        subscribedCount = uiState.subscribedPassageIds.size,
                        onInspectPassage = { viewModel.inspectPassage(it) },
                        onToggleAuthorAdvertise = { viewModel.toggleAuthorAdvertisement() },
                        onTogglePassageAdvertise = { viewModel.toggleAdvertisement(it) },
                        onBulkSetPassageAdvertisement = { type, adv -> viewModel.setPassageTypeAdvertisement(type, adv) },
                        onOpenIdentityDialog = { showIdentityDialog = true },
                        onLogout = { viewModel.logoutIdentity() },
                        onChangePassphrase = { oldPass, newPass -> viewModel.changePassphrase(oldPass, newPass) },
                        onUpdateAuthorSettings = { days, action -> viewModel.updateAuthorSettings(days, action) },
                        onSaveProfile = { u, b, aliasSearch -> viewModel.updateAuthorProfile(u, b, aliasSearch) },
                        onViewSubscribed = { viewModel.setTab(MainTab.SUBSCRIBED) },
                        onPerformInactivityCheck = { viewModel.performInactivityCheck() },
                        onDiscardAuthorIdentity = { action -> viewModel.discardAuthorAccount(action) }
                    )
                }
            }

            // Create Passage Dialog
            if (showCreateDialog) {
                CreatePassageDialog(
                    initialTargetId = createTargetId,
                    onDismiss = { showCreateDialog = false },
                    onSuggestTags = { query -> viewModel.suggestTags(query) },
                    onSubmit = { type, content, tags, parentId, isRecordable, shouldAdvertise, subToId ->
                        viewModel.createPassage(
                            type = type,
                            content = content,
                            tags = tags,
                            parentId = parentId,
                            isRecordable = isRecordable,
                            shouldAdvertise = shouldAdvertise,
                            subscribeToId = subToId
                        )
                    }
                )
            }

            // Sovereign Identity Dialog
            if (showIdentityDialog) {
                IdentityDialog(
                    onDismiss = { showIdentityDialog = false },
                    onCreateNew = { moniker, rememberMe -> viewModel.createIdentity(moniker, rememberMe = rememberMe) },
                    onLogin = { id, pass, rememberMe -> viewModel.loginIdentity(id, pass, rememberMe = rememberMe) },
                    onChangePassphrase = { oldPass, newPass -> viewModel.changePassphrase(oldPass, newPass) }
                )
            }

            // Server Endpoint Dialog
            if (showEndpointDialog) {
                connects.quotes.ui.components.ServerEndpointDialog(
                    currentConfig = uiState.serverEndpoint,
                    isServerAvailable = uiState.isServerAvailable,
                    onDismiss = { showEndpointDialog = false },
                    onSaveEndpoint = { input ->
                        viewModel.updateServerEndpoint(input)
                    }
                )
            }
        }
    }
}
