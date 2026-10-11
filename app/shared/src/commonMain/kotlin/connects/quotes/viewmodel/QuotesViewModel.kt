package connects.quotes.viewmodel

import connects.quotes.client.QuotesApiClient
import connects.quotes.client.SessionManager
import connects.quotes.config.ServerEndpointConfig
import connects.quotes.config.ServerEndpointManager
import connects.quotes.crypto.CryptoKeyPair
import connects.quotes.crypto.EncryptedKeyStoreBlob
import connects.quotes.crypto.LocalChatKeyStore
import connects.quotes.crypto.PlatformCrypto
import connects.quotes.crypto.PlatformEncryptedKeystoreStore
import connects.quotes.database.ClientChatDatabase
import connects.quotes.model.*
import connects.quotes.ui.MainTab
import connects.quotes.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * In-app alert notification when a message arrives while a chat is not open.
 */
data class ChatNotificationAlert(
    val chatId: Id,
    val senderId: Id,
    val text: String,
    val timestamp: Long = 0L
)

/**
 * State representing the entire shared Quotes Connect UI.
 */
data class QuotesUiState(
    val activeTab: MainTab = MainTab.BILLBOARD,
    val billboardItems: List<BillboardItem> = emptyList(),
    val feedItems: List<Passage> = emptyList(),
    val feedDepth: Int = 1,
    val subscribedItems: List<Passage> = emptyList(),
    val subscribedPassageIds: Set<String> = emptySet(),
    val notifications: List<Notification> = emptyList(),
    val topTags: List<Tag> = emptyList(),
    val selectedTag: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Passage>? = null,
    val navigationStack: List<Id> = emptyList(), // Traversal history stack
    val inspectedPassage: Passage? = null,
    val inspectedSubscribers: List<Passage> = emptyList(),
    val inspectedSubscribedTo: List<Passage> = emptyList(),
    val inspectedTree: connects.quotes.graph.GraphEngine.TreeNode? = null,
    val inspectedTreeItems: List<connects.quotes.graph.GraphEngine.FlatTreeItem> = emptyList(),
    val inspectedIsOrphan: Boolean = false,
    val inspectedIsAdvertised: Boolean = false,
    val currentIdentity: Credentials? = null,
    val createdPassages: List<Passage> = emptyList(),
    val advertisedPassageIds: Set<String> = emptySet(),
    val authorPassage: Passage? = null,
    val isAuthorAdvertised: Boolean = false,
    val chatSessions: List<ChatSession> = emptyList(),
    val selectedChatSession: ChatSession? = null,
    val activeChatMessages: List<ChatMessage> = emptyList(),
    val decryptedMessages: Map<String, String> = emptyMap(),
    val isLiveChatConnected: Boolean = false,
    val unreadChatIds: Set<String> = emptySet(),
    val chatNotificationAlert: ChatNotificationAlert? = null,
    val isKeystoreUnlocked: Boolean = false,
    val hasCloudKeystoreBackup: Boolean = false,
    val isPeerTyping: Boolean = false,
    val typingPeerName: String? = null,
    val isChatLoading: Boolean = false,
    val authorSettings: AuthorSettings? = null,
    val authorActivityLogs: List<AuthorActivityLog> = emptyList(),
    val inactivityCheckResult: InactivityCheckResult? = null,
    val isPerformingInactivityCheck: Boolean = false,
    val isLoading: Boolean = false,
    val isServerAvailable: Boolean = true,
    val serverEndpoint: ServerEndpointConfig = ServerEndpointManager.get(),
    val channelSecurityMode: ChannelSecurityMode = ChannelSecurityMode.ASYM_ENCRYPTED,
    val isCheckingConnection: Boolean = false,
    val personalChatNames: Map<String, String> = emptyMap(),
    val userMessage: String? = null
)

/**
 * Shared ViewModel handling business logic, graph traversal, and state updates.
 * Usable across Android, Desktop, Web, and any Compose Multiplatform target.
 */
class QuotesViewModel(
    private val apiClient: QuotesApiClient = QuotesApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(QuotesUiState())
    val uiState: StateFlow<QuotesUiState> = _uiState.asStateFlow()

    private var liveChatJob: Job? = null
    private var liveChatPollingJob: Job? = null
    private var chatTabPollingJob: Job? = null
    private var typingResetJob: Job? = null
    private var notificationStreamJob: Job? = null
    private var isSendingChatMessage = false

    private var isRefreshingAll = false

    init {
        loadPersonalNames()
        scope.launch {
            SessionManager.currentIdentity.collect { creds ->
                _uiState.update { it.copy(currentIdentity = creds) }
                if (creds != null) {
                    ensureNotificationStream(creds.passageId)
                    checkKeystoreStatus(creds.passageId)
                } else {
                    notificationStreamJob?.cancel()
                    notificationStreamJob = null
                    _uiState.update { it.copy(isKeystoreUnlocked = false, hasCloudKeystoreBackup = false) }
                }
            }
        }
        scope.launch {
            ServerEndpointManager.currentEndpoint.collect { ep ->
                _uiState.update { it.copy(serverEndpoint = ep) }
                checkServerConnection()
            }
        }
    }

    fun checkServerConnection() {
        scope.launch {
            _uiState.update { it.copy(isCheckingConnection = true) }
            val available = apiClient.checkServerAvailability(_uiState.value.serverEndpoint)
            _uiState.update {
                it.copy(
                    isServerAvailable = available,
                    channelSecurityMode = apiClient.channelSecurityMode,
                    isCheckingConnection = false,
                    userMessage = if (!available) "Server isn't available at ${it.serverEndpoint.baseUrl}" else null
                )
            }
            if (available) {
                refreshAll()
            }
        }
    }

    fun updateServerEndpoint(input: String) {
        val newConfig = ServerEndpointManager.updateFromInput(input)
        _uiState.update { it.copy(serverEndpoint = newConfig) }
        checkServerConnection()
    }

    fun retryConnection() {
        checkServerConnection()
    }

    fun setTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab, navigationStack = emptyList(), inspectedPassage = null) }
        if (tab == MainTab.CHAT) {
            refreshChats()
            startChatTabPolling()
        } else {
            stopChatTabPolling()
            if (tab == MainTab.SUBSCRIBED) {
                refreshSubscribed()
            }
        }
    }

    private fun startChatTabPolling() {
        chatTabPollingJob?.cancel()
        chatTabPollingJob = scope.launch {
            while (isActive) {
                delay(30000L) // Relaxed 30s safety check; live TCP intent streams handle instant updates
                if (_uiState.value.activeTab == MainTab.CHAT) {
                    val current = _uiState.value.selectedChatSession
                    if (current != null) {
                        refreshChatMessages(current)
                    } else {
                        refreshChats()
                    }
                }
            }
        }
    }

    private fun stopChatTabPolling() {
        chatTabPollingJob?.cancel()
        chatTabPollingJob = null
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = tag) }
        refreshBillboard()
    }

    fun onSearch(query: String) {
        val q = query.trim()
        _uiState.update { it.copy(searchQuery = query) }
        if (q.isBlank()) {
            _uiState.update { it.copy(searchResults = null) }
            return
        }
        scope.launch {
            val results = apiClient.search(q)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isServerAvailable = apiClient.isServerAvailable
                )
            }
        }
    }

    fun inspectPassage(id: Id) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val passage = apiClient.getPassage(id)
            if (passage != null) {
                val subscribers = apiClient.getSubscribers(id)
                val subscribedTo = apiClient.getSubscribedTo(id)
                val isOrphan = apiClient.isOrphan(id)
                val isAdvertised = apiClient.isAdvertised(id)
                val tree = apiClient.getDescendantTree(id)
                val treeItems = tree?.let { with(connects.quotes.graph.GraphEngine) { it.flattenDescendants() } } ?: emptyList()

                _uiState.update { state ->
                    val newStack = if (state.navigationStack.contains(id)) {
                        state.navigationStack.takeWhile { it != id } + id
                    } else {
                        state.navigationStack + id
                    }
                    state.copy(
                        navigationStack = newStack,
                        inspectedPassage = passage,
                        inspectedSubscribers = subscribers,
                        inspectedSubscribedTo = subscribedTo,
                        inspectedTree = tree,
                        inspectedTreeItems = treeItems,
                        inspectedIsOrphan = isOrphan,
                        inspectedIsAdvertised = isAdvertised,
                        isServerAvailable = apiClient.isServerAvailable,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isServerAvailable = apiClient.isServerAvailable,
                        userMessage = "Passage not found"
                    )
                }
            }
        }
    }

    fun popInspection() {
        val stack = _uiState.value.navigationStack
        if (stack.size <= 1) {
            _uiState.update { it.copy(navigationStack = emptyList(), inspectedPassage = null) }
        } else {
            val parentId = stack[stack.size - 2]
            inspectPassage(parentId)
        }
    }

    fun toggleAdvertisement(passageId: Id) {
        scope.launch {
            val authorId = SessionManager.currentAuthorId
            val result = apiClient.toggleAdvertisement(passageId, authorId)
            if (result.isSuccess) {
                refreshAll()
                _uiState.value.inspectedPassage?.let {
                    if (it.id == passageId) {
                        val isAdv = apiClient.isAdvertised(passageId)
                        _uiState.update { s -> s.copy(inspectedIsAdvertised = isAdv) }
                    }
                }
            } else {
                _uiState.update { it.copy(userMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun createPassage(
        type: PassageType,
        content: String,
        tags: List<String>,
        parentId: Id?,
        isRecordable: Boolean,
        shouldAdvertise: Boolean,
        subscribeToId: Id?
    ) {
        scope.launch {
            val result = apiClient.createPassage(
                type = type,
                content = content,
                tags = tags,
                parentId = parentId,
                isRecordable = isRecordable,
                shouldAdvertise = shouldAdvertise,
                subscribeToId = subscribeToId
            )
            if (result.isSuccess) {
                refreshAll()
                if (subscribeToId != null && _uiState.value.inspectedPassage?.id == subscribeToId) {
                    inspectPassage(subscribeToId)
                }
                _uiState.update { it.copy(userMessage = "Passage added to graph") }
            } else {
                _uiState.update { it.copy(userMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun createIdentity(moniker: String, shouldAdvertise: Boolean = false, rememberMe: Boolean = true) {
        scope.launch {
            val creds = apiClient.createIdentity(moniker, shouldAdvertise, rememberMe)
            _uiState.update {
                it.copy(
                    isServerAvailable = apiClient.isServerAvailable,
                    userMessage = "Sovereign identity created: ${creds.passageId.value.take(8)}"
                )
            }
            refreshAll()
        }
    }

    fun toggleAuthorAdvertisement() {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val result = apiClient.toggleAdvertisement(authorId, authorId)
            if (result.isSuccess) {
                val isAdv = apiClient.isAdvertised(authorId)
                _uiState.update {
                    it.copy(
                        isAuthorAdvertised = isAdv,
                        userMessage = if (isAdv) "Profile advertised on Billboard" else "Profile removed from Billboard"
                    )
                }
                refreshBillboard()
            } else {
                _uiState.update { it.copy(userMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun loginIdentity(id: Id, passphrase: String, rememberMe: Boolean = true) {
        scope.launch {
            try {
                apiClient.login(id.value, passphrase, rememberMe)
                _uiState.update {
                    it.copy(
                        currentIdentity = SessionManager.currentIdentity.value,
                        userMessage = if (rememberMe) "Identity connected (Remember Me active)" else "Identity connected (Session only)"
                    )
                }
            } catch (_: Exception) {
                // Offline fallback or legacy login
                SessionManager.setIdentity(Credentials(id, passphrase), rememberMe)
                _uiState.update {
                    it.copy(
                        currentIdentity = Credentials(id, passphrase),
                        userMessage = if (rememberMe) "Identity connected locally (Remember Me active)" else "Identity connected (Session only)"
                    )
                }
            }
            refreshAll()
        }
    }

    fun setRememberMePreference(remember: Boolean) {
        SessionManager.setRememberMePreference(remember)
        _uiState.update {
            it.copy(
                userMessage = if (remember) "Remember Me enabled on this device" else "Remember Me disabled on this device"
            )
        }
    }

    fun logoutIdentity() {
        SessionManager.clearIdentity()
        _uiState.update { it.copy(userMessage = "Disconnected identity. Switched to anonymous mode.") }
        refreshAll()
    }

    fun changePassphrase(oldPassphrase: String, newPassphrase: String) {
        val authorId = SessionManager.currentAuthorId ?: run {
            _uiState.update { it.copy(userMessage = "No sovereign identity currently connected.") }
            return
        }
        scope.launch {
            val result = apiClient.changePassphrase(authorId, oldPassphrase, newPassphrase)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        userMessage = "Passphrase successfully updated!",
                        currentIdentity = SessionManager.currentIdentity.value
                    )
                }
                refreshProfile()
            } else {
                _uiState.update {
                    it.copy(userMessage = result.exceptionOrNull()?.message ?: "Failed to update passphrase.")
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun refreshAll() {
        if (isRefreshingAll) return
        isRefreshingAll = true
        scope.launch {
            try {
                refreshBillboard()
                refreshFeedAndNotifications()
                refreshSubscribed()
                refreshTags()
                refreshProfile()
                refreshChats()
            } finally {
                isRefreshingAll = false
            }
        }
    }

    fun refreshChats() {
        val authorId = SessionManager.currentAuthorId ?: run {
            _uiState.update { it.copy(chatSessions = emptyList(), selectedChatSession = null, activeChatMessages = emptyList(), isLiveChatConnected = false) }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isChatLoading = true) }
            val sessions = apiClient.getChats(authorId)
            _uiState.update { it.copy(chatSessions = sessions, isChatLoading = false) }
            loadPersonalNames()

            // Automatic key-loss detection only operates with an explicitly unlocked local identity.
            // Refreshing chats must never generate a new identity implicitly.
            val myKeyPair = LocalChatKeyStore.getKeyPair(authorId.value)
            if (myKeyPair != null) {
                sessions.forEach { s ->
                    if (s.status == ChatStatus.ACCEPTED && s.type == ChatType.ENCRYPTED) {
                        val myRegisteredKey = if (s.initiatorId == authorId) s.initiatorPublicKey else s.recipientPublicKey
                        if (myRegisteredKey != null && myRegisteredKey != myKeyPair.publicKey) {
                            // Our local key does not match server session (reinstall occurred!). Renew with server!
                            scope.launch {
                                apiClient.renewChatKey(s.id, authorId, myKeyPair.publicKey)
                            }
                        }
                    }
                }
            }

            val currentSelected = _uiState.value.selectedChatSession
            if (currentSelected != null) {
                val matching = sessions.firstOrNull { it.id == currentSelected.id }
                if (matching == null) {
                    // Chat was deleted by peer!
                    _uiState.update {
                        it.copy(
                            selectedChatSession = null,
                            activeChatMessages = emptyList(),
                            userMessage = "Chat conversation was deleted by peer."
                        )
                    }
                    disconnectLiveChat()
                } else if (matching != currentSelected) {
                    _uiState.update { it.copy(selectedChatSession = matching) }
                }
            }
        }
    }

    fun selectChatSession(session: ChatSession?) {
        // Disconnect previous live chat TCP connection immediately
        disconnectLiveChat()

        _uiState.update {
            it.copy(
                selectedChatSession = session,
                isLiveChatConnected = false,
                isPeerTyping = false,
                typingPeerName = null,
                unreadChatIds = if (session != null) it.unreadChatIds - session.id.value else it.unreadChatIds,
                chatNotificationAlert = if (session != null && it.chatNotificationAlert?.chatId == session.id) null else it.chatNotificationAlert
            )
        }

        if (session != null) {
            // 1. Instantly load locally persisted messages & decrypted texts from client-side SQLite database
            val localMessages = ClientChatDatabase.getMessages(session.id)
            val localDecrypted = ClientChatDatabase.getAllDecryptedPlaintexts(session.id)
            if (localMessages.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        activeChatMessages = localMessages,
                        decryptedMessages = state.decryptedMessages + localDecrypted
                    )
                }
            }

            // 2. Open persistent live TCP streaming connection while this chat is open
            connectLiveChat(session)

            // 3. Sync latest messages with server
            refreshChatMessages(session)
        } else {
            _uiState.update { it.copy(activeChatMessages = emptyList()) }
        }
    }

    fun disconnectLiveChat() {
        liveChatJob?.cancel()
        liveChatJob = null
        liveChatPollingJob?.cancel()
        liveChatPollingJob = null
        typingResetJob?.cancel()
        typingResetJob = null
        _uiState.update { it.copy(isLiveChatConnected = false, isPeerTyping = false, typingPeerName = null) }
    }

    private fun connectLiveChat(session: ChatSession) {
        val authorId = SessionManager.currentAuthorId ?: return
        liveChatJob?.cancel()
        liveChatJob = scope.launch {
            while (isActive) {
                try {
                    apiClient.streamLiveChat(session.id, authorId).collect { packet ->
                        when (packet.type) {
                            "CONNECTED" -> {
                                _uiState.update { it.copy(isLiveChatConnected = true) }
                            }
                            "MESSAGE" -> {
                                val msg = packet.message ?: return@collect
                                handleLiveIncomingMessage(session, msg)
                            }
                            "KEY_CHANGED" -> {
                                handlePeerKeyChanged(session, packet.senderId, packet.newPublicKey)
                            }
                            "TYPING" -> {
                                if (packet.senderId != authorId.value) {
                                    val isTyping = packet.isTyping == true
                                    _uiState.update {
                                        it.copy(
                                            isPeerTyping = isTyping,
                                            typingPeerName = packet.senderId
                                        )
                                    }
                                    typingResetJob?.cancel()
                                    if (isTyping) {
                                        typingResetJob = scope.launch {
                                            delay(4000L)
                                            _uiState.update { it.copy(isPeerTyping = false, typingPeerName = null) }
                                        }
                                    }
                                }
                            }
                            "RESYNC_REQUEST" -> {
                                if (packet.senderId != authorId.value) {
                                    handlePeerResyncRequest(session, packet.senderId)
                                }
                            }
                            "STATUS_CHANGED" -> {
                                val pktSession = packet.session
                                if (pktSession != null) {
                                    _uiState.update { state ->
                                        val updatedList = if (state.chatSessions.any { it.id == pktSession.id }) {
                                            state.chatSessions.map { if (it.id == pktSession.id) pktSession else it }
                                        } else {
                                            listOf(pktSession) + state.chatSessions
                                        }
                                        state.copy(
                                            selectedChatSession = if (state.selectedChatSession?.id == pktSession.id) pktSession else state.selectedChatSession,
                                            chatSessions = updatedList
                                        )
                                    }
                                }
                            }
                            "CHAT_DELETED" -> {
                                val cId = packet.chatId
                                _uiState.update { state ->
                                    state.copy(
                                        selectedChatSession = null,
                                        activeChatMessages = emptyList(),
                                        chatSessions = if (cId != null) state.chatSessions.filterNot { it.id.value == cId } else state.chatSessions,
                                        userMessage = "Chat conversation was deleted by peer."
                                    )
                                }
                                disconnectLiveChat()
                            }
                            "ALIAS_PROPOSAL", "ALIAS_ACCEPTED" -> {
                                val pktSession = packet.session
                                if (pktSession != null) {
                                    _uiState.update { state ->
                                        state.copy(
                                            selectedChatSession = if (state.selectedChatSession?.id == pktSession.id) pktSession else state.selectedChatSession,
                                            chatSessions = state.chatSessions.map { if (it.id == pktSession.id) pktSession else it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                _uiState.update { it.copy(isLiveChatConnected = false) }
                delay(2000L) // Reconnect stream if interrupted
            }
        }

        // Persistent TCP-based polling refresh while conversation is actively open
        liveChatPollingJob?.cancel()
        liveChatPollingJob = scope.launch {
            while (isActive) {
                delay(2500L)
                val current = _uiState.value.selectedChatSession
                if (current != null && current.id == session.id) {
                    refreshChatMessages(current)
                }
            }
        }
    }

    private suspend fun handleLiveIncomingMessage(session: ChatSession, msg: ChatMessage) {
        val authorId = SessionManager.currentAuthorId ?: return
        val myKeyPair = LocalChatKeyStore.getKeyPair(authorId.value)
        var plaintext: String? = null

        if (msg.isEncrypted) {
            if (msg.recipientId == authorId && myKeyPair != null) {
                plaintext = PlatformCrypto.decrypt(msg.content, myKeyPair.privateKey)
            } else if (msg.senderId == authorId) {
                plaintext = ClientChatDatabase.getDecryptedPlaintext(msg.id)
            }
        } else {
            plaintext = msg.content
        }

        // Persist message to client-side persistent SQLite database
        ClientChatDatabase.saveMessage(msg, decryptedPlaintext = plaintext)

        _uiState.update { state ->
            val updatedMessages = if (state.activeChatMessages.none { it.id == msg.id }) {
                state.activeChatMessages + msg
            } else {
                state.activeChatMessages
            }
            val updatedDecrypted = state.decryptedMessages.toMutableMap()
            if (plaintext != null) {
                updatedDecrypted[msg.id.value] = plaintext
            }
            state.copy(
                activeChatMessages = updatedMessages,
                decryptedMessages = updatedDecrypted
            )
        }
    }

    private fun handlePeerKeyChanged(session: ChatSession, senderId: String?, newPublicKey: String?) {
        val authorId = SessionManager.currentAuthorId ?: return
        if (newPublicKey.isNullOrBlank()) return

        val updatedSession = if (session.initiatorId == authorId) {
            session.copy(recipientPublicKey = newPublicKey)
        } else {
            session.copy(initiatorPublicKey = newPublicKey)
        }

        _uiState.update { state ->
            state.copy(
                selectedChatSession = updatedSession,
                userMessage = "⚠️ Peer's encryption key changed (reinstall detected). Resending messages with new key exchange..."
            )
        }

        // Resend messages from client-side outgoing stack using new public key
        scope.launch {
            val outgoingStack = ClientChatDatabase.getOutgoingStack(session.id, authorId)
            var resentCount = 0
            for (m in outgoingStack) {
                val plaintext = ClientChatDatabase.getDecryptedPlaintext(m.id)
                if (!plaintext.isNullOrBlank()) {
                    val reEncrypted = PlatformCrypto.encrypt(plaintext, newPublicKey)
                    val recipientId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
                    val res = apiClient.sendChatMessage(
                        chatId = session.id,
                        senderId = authorId,
                        recipientId = recipientId,
                        content = reEncrypted,
                        isEncrypted = true,
                        citedPassageId = m.citedPassageId
                    )
                    if (res.isSuccess) {
                        val resentMsg = res.getOrThrow()
                        ClientChatDatabase.saveMessage(resentMsg, decryptedPlaintext = plaintext)
                        resentCount++
                    }
                }
            }
            if (resentCount > 0) {
                _uiState.update { it.copy(userMessage = "✅ Successfully resent $resentCount encrypted citation(s) to peer after new key exchange.") }
                refreshChatMessages(updatedSession)
            }
        }
    }

    private fun handlePeerResyncRequest(session: ChatSession, requesterId: String?) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val updatedSession = apiClient.getChat(session.id) ?: session
            val peerPubKey = if (updatedSession.initiatorId == authorId) updatedSession.recipientPublicKey else updatedSession.initiatorPublicKey
            if (peerPubKey.isNullOrBlank()) return@launch

            val outgoing = ClientChatDatabase.getOutgoingStack(session.id, authorId)
            var count = 0
            for (m in outgoing) {
                val plaintext = ClientChatDatabase.getDecryptedPlaintext(m.id)
                if (!plaintext.isNullOrBlank()) {
                    val reEncrypted = PlatformCrypto.encrypt(plaintext, peerPubKey)
                    val recipientId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
                    val res = apiClient.sendChatMessage(
                        chatId = session.id,
                        senderId = authorId,
                        recipientId = recipientId,
                        content = reEncrypted,
                        isEncrypted = true,
                        citedPassageId = m.citedPassageId
                    )
                    if (res.isSuccess) count++
                }
            }
            if (count > 0) {
                _uiState.update { it.copy(userMessage = "📤 Resynced and re-encrypted $count message(s) to peer upon resync request.") }
            }
        }
    }

    private fun ensureNotificationStream(authorId: Id) {
        if (notificationStreamJob?.isActive == true) return
        notificationStreamJob = scope.launch {
            while (isActive) {
                try {
                    apiClient.streamNotifications(authorId).collect { packet ->
                        when (packet.type) {
                            "NOTIFICATION" -> {
                                val cId = packet.chatId
                                val currentSelectedId = _uiState.value.selectedChatSession?.id?.value
                                if (cId != null && cId != currentSelectedId) {
                                    _uiState.update { state ->
                                        state.copy(
                                            unreadChatIds = state.unreadChatIds + cId,
                                            chatNotificationAlert = ChatNotificationAlert(
                                                chatId = Id(cId),
                                                senderId = Id(packet.senderId ?: "Peer"),
                                                text = packet.text ?: "New citation message"
                                            )
                                        )
                                    }
                                }
                            }
                            "STATUS_CHANGED" -> {
                                val pktSession = packet.session
                                if (pktSession != null) {
                                    _uiState.update { state ->
                                        val updatedList = if (state.chatSessions.any { it.id == pktSession.id }) {
                                            state.chatSessions.map { if (it.id == pktSession.id) pktSession else it }
                                        } else {
                                            listOf(pktSession) + state.chatSessions
                                        }
                                        state.copy(
                                            chatSessions = updatedList,
                                            selectedChatSession = if (state.selectedChatSession?.id == pktSession.id) pktSession else state.selectedChatSession,
                                            userMessage = if (packet.text != null && packet.senderId != authorId.value) "💬 ${packet.text}" else state.userMessage
                                        )
                                    }
                                }
                            }
                            "CHAT_DELETED" -> {
                                val cId = packet.chatId
                                if (cId != null) {
                                    _uiState.update { state ->
                                        val isCurrent = state.selectedChatSession?.id?.value == cId
                                        state.copy(
                                            chatSessions = state.chatSessions.filterNot { it.id.value == cId },
                                            selectedChatSession = if (isCurrent) null else state.selectedChatSession,
                                            activeChatMessages = if (isCurrent) emptyList() else state.activeChatMessages,
                                            userMessage = if (isCurrent) "Chat conversation was deleted by peer." else state.userMessage
                                        )
                                    }
                                    if (_uiState.value.selectedChatSession == null) {
                                        disconnectLiveChat()
                                    }
                                }
                            }
                            "ALIAS_PROPOSAL", "ALIAS_ACCEPTED" -> {
                                val pktSession = packet.session
                                if (pktSession != null) {
                                    _uiState.update { state ->
                                        state.copy(
                                            chatSessions = state.chatSessions.map { if (it.id == pktSession.id) pktSession else it },
                                            selectedChatSession = if (state.selectedChatSession?.id == pktSession.id) pktSession else state.selectedChatSession
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(3000L) // Reconnect stream if disconnected
            }
        }
    }

    fun dismissChatNotification() {
        _uiState.update { it.copy(chatNotificationAlert = null) }
    }

    fun openChatFromNotification(chatId: Id) {
        dismissChatNotification()
        val session = _uiState.value.chatSessions.firstOrNull { it.id == chatId }
        if (session != null) {
            _uiState.update { it.copy(activeTab = MainTab.CHAT) }
            selectChatSession(session)
        }
    }

    fun refreshChatMessages(session: ChatSession? = _uiState.value.selectedChatSession) {
        val authorId = SessionManager.currentAuthorId ?: return
        val currentSession = session ?: return
        scope.launch {
            val messages = apiClient.getChatMessages(currentSession.id, authorId)
            val myKeyPair = LocalChatKeyStore.getKeyPair(authorId.value)
            val decryptedMap = _uiState.value.decryptedMessages.toMutableMap()

            // Merge with local persistent database
            val localPlaintexts = ClientChatDatabase.getAllDecryptedPlaintexts(currentSession.id)
            decryptedMap.putAll(localPlaintexts)

            messages.forEach { msg ->
                if (msg.isEncrypted) {
                    if (!decryptedMap.containsKey(msg.id.value)) {
                        if (msg.recipientId == authorId) {
                            if (_uiState.value.isKeystoreUnlocked && myKeyPair != null) {
                                val decrypted = PlatformCrypto.decrypt(msg.content, myKeyPair.privateKey)
                                decryptedMap[msg.id.value] = decrypted
                            } else {
                                decryptedMap[msg.id.value] = "[🔒 Locked Citation - Decrypt Keystore with password to view]"
                            }
                        } else if (msg.senderId == authorId) {
                            val cached = ClientChatDatabase.getDecryptedPlaintext(msg.id)
                            if (cached != null) {
                                decryptedMap[msg.id.value] = cached
                            } else {
                                decryptedMap[msg.id.value] = "[Encrypted message sent by you]"
                            }
                        }
                    }
                } else {
                    decryptedMap[msg.id.value] = msg.content
                }
            }

            // Save all messages and plaintexts into client-side SQLite database
            ClientChatDatabase.saveMessages(messages, decryptedMap)

            _uiState.update {
                it.copy(
                    activeChatMessages = messages,
                    decryptedMessages = decryptedMap
                )
            }
        }
    }

    fun loadPersonalNames() {
        val names = ClientChatDatabase.getAllPersonalNames()
        _uiState.update { it.copy(personalChatNames = names) }
    }

    fun setPersonalAlias(chatId: Id, name: String?) {
        if (name.isNullOrBlank()) {
            ClientChatDatabase.deleteChat(chatId) // Or keep messages and clear name
        } else {
            ClientChatDatabase.savePersonalName(chatId, name.trim())
        }
        loadPersonalNames()
    }

    fun deleteChat(chatId: Id) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            _uiState.update { it.copy(isChatLoading = true) }
            apiClient.deleteChat(chatId, authorId)
            ClientChatDatabase.deleteChat(chatId)
            if (_uiState.value.selectedChatSession?.id == chatId) {
                selectChatSession(null)
            }
            refreshChats()
            loadPersonalNames()
            _uiState.update {
                it.copy(
                    isChatLoading = false,
                    userMessage = "Chat conversation deleted."
                )
            }
        }
    }

    fun requestChat(recipientQuery: String, type: ChatType) {
        val authorId = SessionManager.currentAuthorId ?: run {
            _uiState.update { it.copy(userMessage = "Connect your author identity to start a chat.") }
            return
        }
        val trimmedQuery = recipientQuery.trim()
        val isAlias = trimmedQuery.startsWith("@") || !trimmedQuery.contains("-")

        // Check if duplicate already exists locally
        val duplicate = _uiState.value.chatSessions.firstOrNull { session ->
            val otherId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
            val personalName = _uiState.value.personalChatNames[session.id.value]
            (otherId.value.equals(trimmedQuery, ignoreCase = true) ||
             (personalName != null && (personalName.equals(trimmedQuery, ignoreCase = true) || personalName.equals("@$trimmedQuery", ignoreCase = true)))) &&
            session.type == type
        }
        if (duplicate != null) {
            _uiState.update {
                it.copy(
                    userMessage = "Duplicate chat: A ${type.name.lowercase().replace('_', ' ')} conversation already exists with this author.",
                    isChatLoading = false,
                    selectedChatSession = duplicate
                )
            }
            selectChatSession(duplicate)
            return
        }

        scope.launch {
            _uiState.update { it.copy(isChatLoading = true) }
            val initiatorPublicKey = if (type == ChatType.ENCRYPTED || type == ChatType.ENCRYPTED_CLOUD) {
                LocalChatKeyStore.getKeyPair(authorId.value)?.publicKey ?: run {
                    _uiState.update {
                        it.copy(
                            userMessage = "🔒 Unlock your keystore before starting an encrypted chat.",
                            isChatLoading = false
                        )
                    }
                    return@launch
                }
            } else null

            val result = apiClient.requestChat(authorId, Id(trimmedQuery), type, initiatorPublicKey)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                if (isAlias) {
                    val aliasName = if (trimmedQuery.startsWith("@")) trimmedQuery else "@$trimmedQuery"
                    ClientChatDatabase.savePersonalName(session.id, aliasName)
                    loadPersonalNames()
                }
                _uiState.update {
                    it.copy(
                        userMessage = "Chat request sent (${type.name})",
                        isChatLoading = false
                    )
                }
                refreshChats()
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Failed to request chat"
                _uiState.update {
                    it.copy(
                        userMessage = errMsg,
                        isChatLoading = false
                    )
                }
                if (errMsg.contains("Duplicate chat", ignoreCase = true)) {
                    refreshChats()
                }
            }
        }
    }

    fun requestChat(recipientId: Id, type: ChatType) {
        requestChat(recipientId.value, type)
    }

    fun respondChat(chatId: Id, accept: Boolean) {
        val authorId = SessionManager.currentAuthorId ?: return
        val session = _uiState.value.chatSessions.firstOrNull { it.id == chatId }
        scope.launch {
            _uiState.update { it.copy(isChatLoading = true) }
            val recipientPublicKey = if (accept && session != null && (session.type == ChatType.ENCRYPTED || session.type == ChatType.ENCRYPTED_CLOUD)) {
                LocalChatKeyStore.getKeyPair(authorId.value)?.publicKey ?: run {
                    _uiState.update {
                        it.copy(
                            userMessage = "🔒 Unlock your keystore before accepting an encrypted chat.",
                            isChatLoading = false
                        )
                    }
                    return@launch
                }
            } else null

            val result = apiClient.respondChat(chatId, authorId, accept, recipientPublicKey)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        userMessage = if (accept) "Chat request accepted!" else "Chat request declined.",
                        isChatLoading = false
                    )
                }
                refreshChats()
            } else {
                _uiState.update {
                    it.copy(
                        userMessage = result.exceptionOrNull()?.message ?: "Failed to respond to chat",
                        isChatLoading = false
                    )
                }
            }
        }
    }

    
    // ---------------------------------------------------------------------------------------------
    // NEW PRODUCTION KEY EXCHANGE PATH (BUILD 001-F3.1)
    // Canonical flow: Chat opened -> verify participants -> join key relay -> 
    // wait until both active -> authenticated peer handshake -> session key established -> 
    // key confirmation -> secure chat channel established
    // ---------------------------------------------------------------------------------------------
    
    private var clientKeyExchangeManager: connects.quotes.chat.ClientKeyExchangeManager? = null
    
    fun startSecureSessionNegotiation(sessionId: String, peerId: Id, peerPublicKey: String, isInitiator: Boolean) {
        val authorId = SessionManager.currentAuthorId ?: return
        
        clientKeyExchangeManager = connects.quotes.chat.ClientKeyExchangeManager(
            authorId = authorId,
            sendToRelay = { packet ->
                scope.launch {
                    apiClient.sendKeyExchangePacket(packet) // Ensure apiClient has this or similar
                }
            },
            onStateChanged = { sId, state, secret ->
                // Wire into UI state or internal tracking
                if (state == connects.quotes.chat.ExchangeState.ESTABLISHED && secret != null) {
                    _uiState.update { it.copy(userMessage = "Secure session established via Key Relay!") }
                    // TODO: store secret in memory for session
                } else if (state == connects.quotes.chat.ExchangeState.FAILED) {
                    _uiState.update { it.copy(userMessage = "Secure session negotiation failed.") }
                }
            }
        )
        
        scope.launch {
            val kp = SessionManager.currentAuthorId?.let { LocalChatKeyStore.getKeyPair(it.value) }
            if (kp != null) {
                clientKeyExchangeManager?.startExchange(sessionId, peerId, kp, peerPublicKey, isInitiator)
            }
        }
    }
    
    fun handleRelayPacket(packet: connects.quotes.model.KeyExchangePacket) {
        scope.launch {
            clientKeyExchangeManager?.receiveRelayPacket(packet)
        }
    }

    fun sendChatMessage(content: String, citedPassageId: Id? = null) {
        val authorId = SessionManager.currentAuthorId ?: return
        val session = _uiState.value.selectedChatSession ?: return
        if (content.isBlank()) return
        if (isSendingChatMessage) return

        val recipientId = if (session.initiatorId == authorId) session.recipientId else session.initiatorId
        val isEncrypted = session.type == ChatType.ENCRYPTED || session.type == ChatType.ENCRYPTED_CLOUD

        if (isEncrypted && !_uiState.value.isKeystoreUnlocked) {
            _uiState.update { it.copy(userMessage = "🔒 Keystore is locked. Decrypt keystore with your password before sending messages.") }
            return
        }

        isSendingChatMessage = true
        scope.launch {
            try {
                // LEGACY MIGRATION CODE: Old static RSA path. Must be migrated to ephemeral session keys.
                val contentToSend = if (isEncrypted) {
                    val legacyCounterpartyPublicKey = if (session.initiatorId == authorId) {
                        session.recipientPublicKey
                    } else {
                        session.initiatorPublicKey
                    }

                    if (legacyCounterpartyPublicKey.isNullOrBlank()) {
                        _uiState.update { it.copy(userMessage = "Recipient public key is not available yet. Wait for counterparty to accept.") }
                        return@launch
                    }

                    PlatformCrypto.encrypt(content, legacyCounterpartyPublicKey)
                } else {
                    content
                }

                val result = apiClient.sendChatMessage(
                    chatId = session.id,
                    senderId = authorId,
                    recipientId = recipientId,
                    content = contentToSend,
                    isEncrypted = isEncrypted,
                    citedPassageId = citedPassageId
                )

                // Reset typing
                setTyping(session.id, false)

                if (result.isSuccess) {
                    val msg = result.getOrThrow()
                    // Persist client copy to local SQLite database with plaintext
                    ClientChatDatabase.saveMessage(msg, decryptedPlaintext = content)

                    val updatedDecrypted = _uiState.value.decryptedMessages.toMutableMap()
                    updatedDecrypted[msg.id.value] = content // Locally retain plaintext for sender
                    val updatedMessages = if (_uiState.value.activeChatMessages.none { it.id == msg.id }) {
                        _uiState.value.activeChatMessages + msg
                    } else {
                        _uiState.value.activeChatMessages
                    }
                    _uiState.update {
                        it.copy(
                            activeChatMessages = updatedMessages,
                            decryptedMessages = updatedDecrypted
                        )
                    }
                } else {
                    _uiState.update { it.copy(userMessage = result.exceptionOrNull()?.message ?: "Failed to send message") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Message encryption or send failed") }
            } finally {
                isSendingChatMessage = false
            }
        }
    }

    fun checkKeystoreStatus(targetId: Id? = SessionManager.currentAuthorId) {
        val authorId = targetId ?: return
        scope.launch {
            val hasLocal = LocalChatKeyStore.hasKeyPair(authorId.value)
            val cloud = apiClient.getKeyStore(authorId)
            _uiState.update {
                it.copy(
                    isKeystoreUnlocked = hasLocal,
                    hasCloudKeystoreBackup = cloud != null
                )
            }
        }
    }

    fun createAndBackupKeystore(password: String) {
        val authorId = SessionManager.currentAuthorId ?: return
        if (password.isBlank()) return
        scope.launch {
            try {
                LocalChatKeyStore.getOrGenerateKeyPair(authorId.value, password)
                val blob = PlatformEncryptedKeystoreStore.load(authorId.value)
                    ?: throw IllegalStateException("Encrypted local keystore is missing after creation")
                val payload = KeyStorePayload(
                    authorId = authorId,
                    encryptedPayload = blob.encryptedPayload,
                    salt = blob.salt,
                    iv = blob.iv,
                    version = blob.version,
                    updatedAt = currentTimeMillis()
                )
                val ok = apiClient.backupKeyStore(payload)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            isKeystoreUnlocked = true,
                            hasCloudKeystoreBackup = true,
                            userMessage = "✅ Sovereign Keystore created and backed up to cloud."
                        )
                    }
                    refreshChatMessages()
                } else {
                    _uiState.update { it.copy(userMessage = "Failed to backup keystore to cloud.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Failed to create keystore") }
            }
        }
    }

    fun unlockKeystore(password: String) {
        val authorId = SessionManager.currentAuthorId ?: return
        if (password.isBlank()) return
        scope.launch {
            val cloud = apiClient.getKeyStore(authorId)
            if (cloud == null) {
                _uiState.update { it.copy(userMessage = "No keystore backup found in cloud.") }
                return@launch
            }
            try {
                val blob = EncryptedKeyStoreBlob(
                    encryptedPayload = cloud.encryptedPayload,
                    salt = cloud.salt,
                    iv = cloud.iv,
                    version = cloud.version
                )
                // Restore the exact opaque envelope first. Decryption remains inside
                // LocalChatKeyStore so a cloud restore never creates a second ciphertext.
                LocalChatKeyStore.restoreEncryptedBlob(authorId.value, blob)
                LocalChatKeyStore.unlock(authorId.value, password)
                _uiState.update {
                    it.copy(
                        isKeystoreUnlocked = true,
                        hasCloudKeystoreBackup = true,
                        userMessage = "✅ Keystore unlocked. Encrypted citations decrypted."
                    )
                }
                refreshChatMessages()
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "❌ Incorrect password. Unable to unlock keystore.") }
            }
        }
    }

    fun lockKeystore() {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            LocalChatKeyStore.lock(authorId.value)
            _uiState.update {
                it.copy(
                    isKeystoreUnlocked = false,
                    userMessage = "🔒 Keystore locked. Local keys wiped from memory."
                )
            }
            refreshChatMessages()
        }
    }

    fun resetKeystore(newPassword: String) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            try {
                // 1. Delete from cloud
                apiClient.deleteKeyStore(authorId)
                // 2. Clear local key pair
                LocalChatKeyStore.deleteKeyPair(authorId.value)
                // 3. Generate new key pair
                val newKp = PlatformCrypto.generateKeyPair()
                LocalChatKeyStore.saveKeyPair(authorId.value, newKp, newPassword)
                // 4. Read the exact locally persisted encrypted envelope and back it up.
                val blob = PlatformEncryptedKeystoreStore.load(authorId.value)
                    ?: throw IllegalStateException("Encrypted local keystore is missing after reset")
                val payload = KeyStorePayload(
                    authorId = authorId,
                    encryptedPayload = blob.encryptedPayload,
                    salt = blob.salt,
                    iv = blob.iv,
                    version = blob.version,
                    updatedAt = currentTimeMillis()
                )
                apiClient.backupKeyStore(payload)

                // 5. Send new encryption key to every encrypted chat
                val encryptedSessions = _uiState.value.chatSessions.filter {
                    it.type == ChatType.ENCRYPTED || it.type == ChatType.ENCRYPTED_CLOUD
                }
                for (s in encryptedSessions) {
                    apiClient.renewChatKey(s.id, authorId, newKp.publicKey)
                }

                _uiState.update {
                    it.copy(
                        isKeystoreUnlocked = true,
                        hasCloudKeystoreBackup = true,
                        userMessage = "✅ Keystore reset. New keys generated, synced to cloud, and published to ${encryptedSessions.size} chat(s). You can now request resync from peers."
                    )
                }
                refreshChats()
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Failed to reset keystore") }
            }
        }
    }

    fun setTyping(chatId: Id, isTyping: Boolean) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            apiClient.setTyping(chatId, authorId, isTyping)
        }
    }

    fun requestChatResync(chatId: Id) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            apiClient.requestChatResync(chatId, authorId)
            _uiState.update { it.copy(userMessage = "🔄 Chat resync requested from peer. Awaiting historical messages...") }
        }
    }

    fun refreshProfile() {
        scope.launch {
            val authorId = SessionManager.currentAuthorId
            if (authorId != null) {
                // Ping author active
                apiClient.pingAuthorActive(authorId)

                val created = apiClient.getCreatedPassages(authorId)
                val authorP = apiClient.getPassage(authorId) ?: created.firstOrNull { it.id == authorId }
                val isAdv = apiClient.isAdvertised(authorId)
                val settings = apiClient.getAuthorSettings(authorId)
                val advPassageIds = apiClient.getAdvertisedPassageIds(authorId)
                _uiState.update {
                    it.copy(
                        createdPassages = created,
                        advertisedPassageIds = advPassageIds,
                        authorPassage = authorP,
                        isAuthorAdvertised = isAdv,
                        authorSettings = settings,
                        isServerAvailable = apiClient.isServerAvailable
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        createdPassages = emptyList(),
                        advertisedPassageIds = emptySet(),
                        authorPassage = null,
                        isAuthorAdvertised = false,
                        authorSettings = null
                    )
                }
            }
        }
    }

    fun setPassageTypeAdvertisement(type: String?, advertise: Boolean) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val res = apiClient.setPassageTypeAdvertisement(authorId, type, advertise)
            if (res.isSuccess) {
                val count = res.getOrThrow()
                val actionName = if (advertise) "advertised on" else "removed from"
                val targetName = type ?: "ALL"
                _uiState.update { it.copy(userMessage = "✓ $count $targetName passage(s) $actionName Billboard") }
                refreshProfile()
                refreshBillboard()
            } else {
                _uiState.update { it.copy(userMessage = res.exceptionOrNull()?.message ?: "Failed to update visibility") }
            }
        }
    }

    fun updateAuthorSettings(inactivityPeriodDays: Int, discardAction: AuthorDiscardAction) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val updated = apiClient.updateAuthorSettings(authorId, inactivityPeriodDays, discardAction)
            if (updated != null) {
                _uiState.update {
                    it.copy(
                        authorSettings = updated,
                        userMessage = "Inactivity period set to ${inactivityPeriodDays}d (${discardAction.name})"
                    )
                }
                refreshProfile()
            }
        }
    }

    fun updateAuthorProfile(username: String?, bio: String?, aliasSearchEnabled: Boolean? = null) {
        val authorId = SessionManager.currentAuthorId ?: return
        val currentDays = _uiState.value.authorSettings?.inactivityPeriodDays ?: 30
        val currentAction = _uiState.value.authorSettings?.discardAction ?: AuthorDiscardAction.ORPHAN
        val currentAliasSearch = aliasSearchEnabled ?: _uiState.value.authorSettings?.aliasSearchEnabled ?: true
        scope.launch {
            val updated = apiClient.updateAuthorSettings(authorId, currentDays, currentAction, username, bio, currentAliasSearch)
            if (updated != null) {
                _uiState.update {
                    it.copy(authorSettings = updated, userMessage = "✓ Author profile and alias updated.")
                }
                refreshProfile()
            }
        }
    }

    fun refreshSubscribed() {
        val authorId = SessionManager.currentAuthorId ?: run {
            _uiState.update { it.copy(subscribedItems = emptyList(), subscribedPassageIds = emptySet()) }
            return
        }
        scope.launch {
            val list = apiClient.getSubscribed(authorId)
            val ids = list.map { it.id.value }.toSet()
            _uiState.update {
                it.copy(subscribedItems = list, subscribedPassageIds = ids)
            }
        }
    }

    fun toggleDirectSubscription(passageId: Id) {
        val authorId = SessionManager.currentAuthorId ?: run {
            _uiState.update { it.copy(userMessage = "Connect your author identity to subscribe.") }
            return
        }
        scope.launch {
            val isSubbed = _uiState.value.subscribedPassageIds.contains(passageId.value)
            val success = if (isSubbed) {
                apiClient.unsubscribeAuthorPassage(authorId, passageId)
            } else {
                apiClient.subscribeAuthorPassage(authorId, passageId)
            }
            if (success) {
                _uiState.update { state ->
                    val newIds = if (isSubbed) state.subscribedPassageIds - passageId.value else state.subscribedPassageIds + passageId.value
                    state.copy(
                        subscribedPassageIds = newIds,
                        userMessage = if (isSubbed) "Unsubscribed from passage" else "★ Subscribed to passage"
                    )
                }
                refreshSubscribed()
            }
        }
    }

    fun proposeChatAlias(chatId: Id, alias: String) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val ok = apiClient.proposeChatAlias(chatId, authorId, alias)
            if (ok) {
                _uiState.update { it.copy(userMessage = "🤝 Shared chat alias proposed to peer.") }
                refreshChats()
            }
        }
    }

    fun acceptChatAlias(chatId: Id) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val ok = apiClient.acceptChatAlias(chatId, authorId)
            if (ok) {
                _uiState.update { it.copy(userMessage = "✓ Shared chat alias accepted!") }
                refreshChats()
            }
        }
    }

    fun performInactivityCheck() {
        scope.launch {
            _uiState.update { it.copy(isPerformingInactivityCheck = true) }
            val result = apiClient.performInactivityCheck()
            _uiState.update {
                it.copy(
                    inactivityCheckResult = result,
                    isPerformingInactivityCheck = false,
                    userMessage = if (result != null) {
                        "Inactivity sweep: ${result.purgedAuthorsCount} purged, ${result.deletedChatsCount} chats deleted, ${result.orphanedPassagesCount} orphaned, ${result.deletedPassagesCount} deleted"
                    } else "Inactivity check completed"
                )
            }
            refreshAll()
        }
    }

    fun discardAuthorAccount(action: AuthorDiscardAction? = null) {
        val authorId = SessionManager.currentAuthorId ?: return
        scope.launch {
            val ok = apiClient.discardAuthor(authorId, action)
            if (ok) {
                logoutIdentity()
                _uiState.update {
                    it.copy(userMessage = "Author identity discarded and purged according to selected rule.")
                }
                refreshAll()
            } else {
                _uiState.update { it.copy(userMessage = "Failed to discard author identity.") }
            }
        }
    }

    private fun refreshBillboard() {
        scope.launch {
            val items = apiClient.getBillboard(_uiState.value.selectedTag)
            _uiState.update {
                it.copy(
                    billboardItems = items,
                    isServerAvailable = apiClient.isServerAvailable
                )
            }
        }
    }

    fun setFeedDepth(depth: Int) {
        val safe = depth.coerceIn(1, 5)
        _uiState.update { it.copy(feedDepth = safe) }
        refreshFeedAndNotifications()
    }

    private fun refreshFeedAndNotifications() {
        scope.launch {
            val authorId = SessionManager.currentAuthorId
            if (authorId != null) {
                val feed = apiClient.getFeed(authorId, depth = _uiState.value.feedDepth)
                val notifs = apiClient.getNotifications(authorId)
                _uiState.update {
                    it.copy(
                        feedItems = feed,
                        notifications = notifs,
                        isServerAvailable = apiClient.isServerAvailable
                    )
                }
            } else {
                _uiState.update { it.copy(feedItems = emptyList(), notifications = emptyList()) }
            }
        }
    }

    private fun refreshTags() {
        scope.launch {
            val tags = apiClient.getTopTags()
            _uiState.update {
                it.copy(
                    topTags = tags,
                    isServerAvailable = apiClient.isServerAvailable
                )
            }
        }
    }

    suspend fun suggestTags(query: String): List<Tag> {
        return apiClient.suggestTags(query, limit = 8)
    }
}
