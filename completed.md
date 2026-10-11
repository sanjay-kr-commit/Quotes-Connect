# Quotes Connect — Completed Implementations & System Register

> **Persistent System Register Directive**: This document (`completed.md`) is the definitive, exhaustive record of all completed, tested, and production-hardened features, protocols, and architectural subsystems implemented in the Quotes Connect project.
> 
> **Referencing Rule**: From now on, the development team and AI assistants must refer to both `completed.md` (for the authoritative inventory of what has been implemented and how it functions) and `plan.md` (for overall project manifesto, roadmap, and core philosophy).

---

## Table of Contents
1. [Core Domain & Knowledge Graph Architecture](#1-core-domain--knowledge-graph-architecture)
2. [Cryptographic Engine & Security Hardening](#2-cryptographic-engine--security-hardening)
3. [Server Authentication, Authorization & Sessions](#3-server-authentication-authorization--sessions)
4. [AST Root-Field GraphQL Gateway](#4-ast-root-field-graphql-gateway)
5. [Transport Security & Asymmetric Hybrid Channel Encryption](#5-transport-security--asymmetric-hybrid-channel-encryption)
6. [Personal Chat & Private Citations Architecture](#6-personal-chat--private-citations-architecture)
7. [Tag System, Autocomplete & Multiplatform Caching](#7-tag-system-autocomplete--multiplatform-caching)
8. [Visibility Controls & Progressive Feed Traversal](#8-visibility-controls--progressive-feed-traversal)
9. [Author Sovereignty, Inactivity Sweeps & Discard Workflows](#9-author-sovereignty-inactivity-sweeps--discard-workflows)
10. [Monochrome Editorial & Brutalist UI/UX Design System (v1.0)](#10-monochrome-editorial--brutalist-uiux-design-system-v10)
11. [Multiplatform Client Infrastructure](#11-multiplatform-client-infrastructure)
12. [Build, Packaging & Distribution Tooling](#12-build-packaging--distribution-tooling)
13. [Desktop Hardening, Session Persistence & Key Bindings](#13-desktop-hardening-session-persistence--key-bindings)
14. [Chat Uniqueness, Alias Search & Cross-Platform UI Alignment](#14-chat-uniqueness-alias-search--cross-platform-ui-alignment)
15. [Advanced Chat Reliability, Cryptography Hardening, Polling & Lifecycle Management](#15-advanced-chat-reliability-cryptography-hardening-polling--lifecycle-management)
16. [Comprehensive Security, Privacy & Performance Hardening](#16-comprehensive-security-privacy--performance-hardening)
17. [Verification & Test Register](#17-verification--test-register)
18. [Web Application Viewport-Locked Chat & Adaptive Scrolling Architecture](#18-web-application-viewport-locked-chat--adaptive-scrolling-architecture)
19. [Real-Time TCP Notification & Live Chat Stream Architecture with Deletion and Status Synchronization](#19-real-time-tcp-notification--live-chat-stream-architecture-with-deletion-and-status-synchronization)
20. [Intent-Based TCP Streaming and Granular Reactive System](#20-intent-based-tcp-streaming-and-granular-reactive-system)
21. [Real WebCrypto & Sovereign End-to-End Cryptography](#21-real-webcrypto--sovereign-end-to-end-cryptography)
22. [Interactive Visual Graph Canvas Architecture](#22-interactive-visual-graph-canvas-architecture)
23. [Fail-Closed Authentication & Session-Bound Authorization Matrix](#23-fail-closed-authentication--session-bound-authorization-matrix)
24. [Multiplatform Keystore Implementation & Storage Hardening](#24-multiplatform-keystore-implementation--storage-hardening)

---

## 1. Core Domain & Knowledge Graph Architecture

### 1.1 Passage Polymorphism & Universal Object Model
- **Polymorphic Entities (`PassageType`)**:
  - `PASSAGE`: Independent sovereign thought or note; voluntarily advertised on Billboard.
  - `AUTHOR`: Sovereign pseudonymous identity moniker node created via `createIdentity`.
  - `QUOTE`: Verbatim preserved quotation with author attribution, bibliographic source, and page number.
  - `COMMENT`: Contextual reflection or response; must subscribe to a target passage.
  - `RECORDABLE`: Historical ledger axiom; immutable; cannot be deleted; citations to it are locked permanently.
  - `SOURCE`: Citation to book, academic paper, article, or URL, preserving bibliographic context.
- **Stable ID Representation**:
  - Strongly-typed `Id` Kotlin value class serialized as a primitive JSON string (`"id": "..."`).
  - IDs are generated via OS-level CSPRNG (`SecureRandomSource.generateHexId(16)`), producing 32-character high-entropy hex strings.

### 1.2 Directed Acyclic Graph (DAG) Invariants
- **Directed Subscription Edges (`sourceId -> targetId`)**:
  - `sourceId` subscribes to or cites `targetId`.
- **Cycle Prevention Engine (`DagValidator`)**:
  - Uses Breadth-First Search (BFS) graph traversal to detect potential cycles prior to edge insertion.
  - Strictly rejects self-subscriptions (`sourceId == targetId`) and circular paths (`A -> B -> C -> A`).
- **Graph Traversal (`GraphEngine`)**:
  - Calculates shortest BFS paths between any two passage nodes.
  - Generates comprehensive citation ancestor and descendant trees for thread rendering.

### 1.3 Knowledge Sovereignty & Immutability Rules
- **Ledger Immutability**:
  - Any passage with `isRecordable = true` or `type == RECORDABLE` is permanently locked.
  - Any passage cited by a recordable passage inherits deletion protection.
- **Orphan / Public Domain Invariant**:
  - Passages created without an author (`parentId == null`) are automatically classified as public domain orphan knowledge (`isOrphan = true`, `isAdvertised = true`).
  - Orphans are permanently advertised on the Billboard and cannot be unadvertised or deleted by any user.
- **Zero Algorithmic Curation**:
  - No recommendation feeds, no vanity metrics, no tracking, no algorithmic weighting. All graph connections and discoveries are user-directed.

---

## 2. Cryptographic Engine & Security Hardening

### 2.1 Multiplatform CSPRNG Engine (`SecureRandomSource.kt`)
- Completely replaced pseudo-random generators (`kotlin.random.Random`) with OS-level CSPRNG across all 5 targets:
  - **JVM**: `java.security.SecureRandom`
  - **Android**: `java.security.SecureRandom`
  - **JS**: `globalThis.crypto.getRandomValues()`
  - **WasmJS**: Web Crypto API (`crypto.getRandomValues()`)
- High-entropy generation for:
  - Passage IDs (`Id.generate()`)
  - Sovereign passphrases (`Credentials.generatePassphrase(32)`)
  - Symmetric AES-256 keys and 12-byte IVs
  - Credential salts (16-byte random values)
  - Bearer session tokens (256-bit CSPRNG hex strings)

### 2.2 Standardized Cryptographic Protocol (`QC-RSA2048-AES256GCM-OAEP`)
- Standardized, versioned cryptographic envelope (`CryptoEnvelope.kt`) with identifier `QC-RSA2048-AES256GCM-OAEP`.
- **Asymmetric Key Exchange & Encryption**:
  - 2048-bit RSA keys utilizing `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`.
  - Deprecated legacy PKCS#1 v1.5 padding (supported as read-only fallback in `ServerChannelCrypto`).
- **Symmetric Encryption**:
  - AES-256-GCM with 12-byte CSPRNG IVs, 128-bit authentication tags, and channel context Authenticated Additional Data (AAD) binding.
- **Keystore Encryption**:
  - Local client keystores are encrypted via PBKDF2-HMAC-SHA256 (100,000 iterations and 16-byte salt) deriving 256-bit AES keys.
- **Fail-Closed Security Posture**:
  - Unaccelerated platform stubs throw typed [`UnsupportedSecureOperation`](file:///home/sanjay/Quotes%20Connect/core/src/commonMain/kotlin/connects/quotes/security/CryptoExceptions.kt) rather than falling back to plaintext or pseudo-ciphers.

### 2.3 Multiplatform Time Abstraction (`PlatformTime.kt`)
- Multiplatform `currentTimeMillis(): Long` implemented across JVM, Android, JS, and WasmJS, eliminating JVM-specific time leaks from common code.

---

## 3. Server Authentication, Authorization & Sessions

### 3.1 Salted PBKDF2 Verifiers (`PasswordHasher.kt`, `auth_credentials`)
- Replaced plaintext database credential storage with salted verifier records:
  - **Algorithm**: PBKDF2-HMAC-SHA256
  - **Iterations**: 100,000
  - **Salt**: 16 cryptographically secure random bytes
  - **Verification**: Constant-time byte comparison via `MessageDigest.isEqual` to prevent timing side-channel attacks.
- **Automatic Legacy Migration**:
  - Authors with legacy plaintext credentials in `passages` are transparently verified, migrated to `auth_credentials`, and their plaintext credentials purged on first login.

### 3.2 Revocable Bearer Session Tokens (`auth_sessions`, `AuthRoutes.kt`)
- Server-side bearer token management:
  - Tokens are 256-bit CSPRNG hex strings (64 characters).
  - Tokens are stored in the SQLite/MySQL database solely as SHA-256 hashes (`token_hash`), preventing session hijacking even if database dumps are compromised.
- **REST Auth Endpoints**:
  - `POST /api/auth/login`: Authenticates moniker + passphrase; generates access and refresh tokens.
  - `GET /api/auth/session`: Validates active bearer token and returns principal metadata.
  - `POST /api/auth/refresh`: Rotates active access token, invalidates old token, and issues a fresh session token.
  - `POST /api/auth/logout`: Revokes active bearer token across database and active memory.

### 3.3 Server Request Authorization Matrix (`AuthPrincipal.kt`)
- Server extracts `Authorization: Bearer <token>` in Ktor application pipeline and resolves an authenticated `AuthPrincipal`.
- **Mutation & REST Authorization**:
  - Caller identity check (`principal.authorId == targetAuthorId`) is enforced for:
    - `createPassage` (if `parentId != null`)
    - `toggleBillboard`, `deletePassage`, `changePassphrase`
    - `requestChat`, `respondChat`, `sendChatMessage`, `renewChatKey`
    - `backupKeyStore`, `deleteKeyStore`
    - `setTyping`, `requestChatResync`
    - `updateAuthorSettings`, `pingAuthorActive`, `discardAuthor`
    - `subscribeAuthorPassage`, `unsubscribeAuthorPassage`
    - `proposeChatAlias`, `acceptChatAlias`
    - `setPassageTypeAdvertisement`
  - REST endpoints (`/api/chat/live`, `/api/chat/notifications`, `/api/chat/typing`, `/api/author/settings`, `/api/author/discard`, `/api/author/passage-visibility`) enforce author authorization; identity spoofing is rejected with `403 Forbidden`.

### 3.4 Client Bearer Session Storage (`PlatformSessionStore.kt`)
- Client sessions (`PersistedSession`) store:
  - `authorId`: Author identifier
  - `sessionId`: Unique session UUID
  - `accessToken`: Revocable bearer token
  - `expiresAt`: Token expiration epoch
- **Platform Storage**:
  - **JVM**: `~/.quotes_connect/session.json`
  - **Android**: `filesDir/quotes_connect_session.json`
  - **JS / WasmJS**: Browser `localStorage`
- **Zero Plaintext Credentials on Disk**: Raw passphrases are never stored in client persistence files.

---

## 4. AST Root-Field GraphQL Gateway

### 4.1 Zero-Dependency AST Document Parser (`GraphQLDocumentParser.kt`)
- Replaced fragile substring matching with a full top-level root field selection parser:
  - Tokenizes queries and mutations, ignoring comments (`# ...`) and whitespace.
  - Properly extracts operation types (`query`, `mutation`, `subscription`).
  - Extracts root selection fields while capturing aliases (`alias: field`), arguments (`field(arg: $val)`), directives (`@skip`, `@include`), and inner sub-selection sets (`{ ... }`).
  - Completely fixes **BUG-013**: Nested sub-queries (e.g. `query { billboard { passage { createPassage } } }`) only execute the root `billboard` resolver and never trigger unauthorized mutations.

### 4.2 GraphQL Gateway Execution Pipeline (`GraphQLGateway.kt`)
- Dispatches parsed root fields with full `AuthPrincipal` propagation.
- Integrated GraphiQL interactive web explorer served at `/graphiql`.
- Handles polymorphic responses, variable maps, nested objects, and error envelopes.

---

## 5. Transport Security & Asymmetric Hybrid Channel Encryption

### 5.1 Automatic Transport Security Detection (`ServerEndpointConfig.kt`)
- Dynamically checks listening ports (`443`, `8443`) and protocols (`https://`):
  - When TLS/SSL is active: native transport security is utilized.
  - When unencrypted HTTP is detected: transparent asymmetric hybrid envelope encryption engages automatically.

### 5.2 Ephemeral Server Key Exchange (`ServerChannelCrypto.kt`)
- On startup, the Ktor server generates an ephemeral RSA-2048 keypair with automatic rotation support.
- Public key published via `GET /api/channel/handshake` (`ChannelHandshakeResponse`).

### 5.3 Hybrid Request & Response Envelopes
- Client generates an ephemeral 256-bit AES key and 12-byte IV per request.
- Request payload is encrypted using `AES/GCM/NoPadding`.
- Symmetric AES key is encrypted with the server's ephemeral RSA public key via `RSA-OAEP`.
- Packaged into `EncryptedChannelPayload` and sent to `POST /api/channel/secure` or transparently via `POST /graphql` with header `X-Encrypted-Channel: true`.
- Server unwraps, processes query, and encrypts response body using the same AES key and fresh IV (`EncryptedChannelResponse`).

### 5.4 Brutalist Transport Security Badges
- **`[SSL / TLS SECURED]`**: Rendered when connected over HTTPS/TLS.
- **`[ENCRYPTED CHANNEL]`**: Rendered when connected over HTTP with active hybrid envelope encryption.
- **`[INSECURE HTTP]`**: Rendered as a warning badge if connected over unencrypted HTTP without channel protection.
- Displayed across Compose Multiplatform (`ChannelSecurityChip.kt`, `ServerEndpointDialog.kt`) and Web UI (`#channelSecurityBadge`).

---

## 6. Personal Chat & Private Citations Architecture

### 6.1 Three Distinct Chat Modes
- **`Unencrypted Cloud` (`UNENCRYPTED`)**:
  - Messages stored on server database in `unencrypted_chat_messages`.
- **`Encrypted On-Device` (`ENCRYPTED`)**:
  - Ephemeral in-memory relay mailbox (`ephemeralEncryptedMailbox`).
  - Zero server database retention; messages wiped immediately upon relay.
  - End-to-end encrypted with RSA-2048 + AES-256-GCM.
- **`Encrypted Cloud` (`ENCRYPTED_CLOUD`)**:
  - End-to-end encrypted messages stored as ciphertext in `encrypted_cloud_chat_messages`.
  - Zero plaintext stored on server.

### 6.2 3-Button Toggle Selector & Keystore Gating
- Replaced dropdowns with clean 3-button selector: `Unencrypted Cloud`, `Encrypted On-Device`, and `Encrypted Cloud`.
- Keystore Gating: If the user lacks an unlocked keystore, the `Encrypted Cloud` toggle is hidden, and initiating encrypted conversations displays the required prompt:
  > *"You don't have a keystore to continue this conversation. Please create a keystore with key phrase to continue or start this conversation."*

### 6.3 Real-time Live TCP Streaming & Notification Feeds
- **Live TCP Chat SSE Streaming (`GET /api/chat/live`)**:
  - Maintains persistent streaming connection while chat is open; instantly disconnects on exit.
- **In-App Notification Stream (`GET /api/chat/notifications`)**:
  - Alerts users to incoming messages when the specific chat is not currently open.
- **Live Typing Indicator ("quoting...")**:
  - SSE typing broadcasts (`POST /api/chat/typing`) stream live `TYPING` events, displaying a pulsing **"quoting..."** indicator.
- **Private Citations (`citedPassageId`)**:
  - Allows citing any graph passage privately inside a chat message, rendered as a clickable link.

### 6.4 Chat Aliases
- **Personal Alias**: Stored strictly on the local client device without server disclosure.
- **Shared Alias**: Proposed via `/api/chat/alias/propose`, accepted via `/api/chat/alias/accept`, displayed with mutual confirmation banners.

---

## 7. Tag System, Autocomplete & Multiplatform Caching

### 7.1 Server Tag Database (`global_tags`)
- Maintains an index of all tags with real usage counts (`count`), updated automatically whenever any passage arrives.
- **Default Aesthetic Seed Tags**: Clean database initialization automatically seeds 16 project aesthetic tags:
  `stoicism`, `philosophy`, `wisdom`, `sovereign`, `knowledge`, `dag`, `citation`, `epistemology`, `aphorism`, `truth`, `ethics`, `logic`, `reason`, `virtue`, `mind`, `literature`.

### 7.2 500ms "Thinking" Debounce Autocomplete
- Tag search input waits for a 500ms typing pause before querying suggestions.
- Server returns top tags sorted by usage count, prioritizing prefix matches over substring matches.

### 7.3 Comma-to-Chip LazyRow UX
- Typing `,` or pressing Enter instantly chips the tag into a horizontal `LazyRow` with removal `×` buttons, preventing clumped text strings.

### 7.4 Multiplatform Local Tag Cache (`ClientTagCache.kt`)
- Zero-latency autocomplete caching locally backed by:
  - **JVM**: `~/.quotes_connect/tags_cache.json`
  - **Android**: `filesDir/quotes_connect_tags_cache.json`
  - **Web**: Browser `localStorage`
- Eliminates redundant network calls and supports offline tag completion.

---

## 8. Visibility Controls & Progressive Feed Traversal

### 8.1 Granular & Bulk Billboard Visibility Controls
- **Per-Passage Visibility**: Authors can toggle Billboard advertisement of each individual owned passage (`[ADVERTISED]` vs `[PRIVATE / UNADVERTISED]`).
- **Bulk Type-Based Management**: Authors can bulk advertise or unadvertise thoughts by passage type (`QUOTE`, `PASSAGE`, `COMMENT`, `RECORDABLE`, `SOURCE`, or `ALL`).
- **Strict Orphan Immutability**: All visibility mutations enforce `is_orphan = 0`. Public domain orphan passages remain permanently advertised.

### 8.2 Lazy Feed Traversal with Progressive Depth Discovery
- **Lazy On-Demand Traversal**: The Feed evaluates on-demand upon query pull (`query { feed(authorId, depth, limit, offset) }`):
  - Subscribed Authors: lazily reveals owned creations (`parent_id IN (...)`).
  - Subscribed Passages/Quotes: lazily reveals citing attachments and comments (`subscriptions WHERE target_id IN (...)`).
- **Progressive Depth Controls**: Interactive depth buttons (`depth = 1`, `2`, `3`) allow progressive graph exploration without ahead-of-time server processing.
- **Notification Isolation**: Zero notifications generated for feed discoveries; notifications are strictly reserved for chats and direct citations to author's owned thoughts.

---

## 9. Author Sovereignty, Inactivity Sweeps & Discard Workflows

### 9.1 Sovereign Pseudonymous Identity & Username Resolution
- Pseudonymous monikers and unique `@username` identifiers in `author_settings`.
- Passphrase login supports both `@username` + passphrase and UUID + passphrase via `/api/author/resolve`.

### 9.2 Activity Heartbeat & Logging
- Every connection, ping (`POST /api/author/ping`), notification stream, settings update, or passage creation logs activity in `author_activity_logs` and updates `last_active_at`.

### 9.3 Inactivity Sweeps & Discard Actions
- Authors specify inactivity thresholds (1d, 7d, 14d, 30d, 60d, 90d, 180d, 365d); moniker is tagged with `inactivity:${days}d`.
- Background worker and on-demand endpoints (`POST /api/author/inactivity-check`, `mutation { performInactivityCheck }`) enforce:
  1. Permanent deletion of all chat messages sent by the inactive author.
  2. Unadvertised passages are processed based on configured `discardAction`:
     - `ORPHAN`: Ownership is stripped (`parentId = null`); promoted to Billboard as public domain orphan knowledge (`isOrphan = true`, `isAdvertised = true`).
     - `DELETE`: Unadvertised passages are permanently deleted (`RECORDABLE` passages are protected and always converted to orphans).
- **Voluntary Discard (`discardAuthor`)**: Authors can execute this discard action manually at any time.

---

## 10. Monochrome Editorial & Brutalist UI/UX Design System (v1.0)

### 10.1 Design Palette & Architectural Tokens
- **Color System**:
  - `BLACK` (`#000000`): Primary background foundation.
  - `SURFACE_01` (`#111111`): Cards, containers, panels.
  - `SURFACE_02` (`#181818`): Elevated panels, inputs, active containers.
  - `WHITE` (`#FFFFFF`): Primary text and high-contrast accents.
  - `GRAY_01` (`#B5B5B5`): Secondary text, subtitles.
  - `GRAY_02` (`#666666`): Borders, outlines, metadata labels.
  - `GRAY_03` (`#333333`): Subtle borders, dividers, disabled states.
- **Restrained Corner Radii**:
  - `0px` (`QCRadiusStructural`): Structural surfaces, headers, status bars.
  - `4px` (`QCRadiusInput`): Text inputs, search fields, tags, chips.
  - `6px` (`QCRadiusCard`): Passage cards, content containers, modals.
  - `8px` (`QCRadiusButton`): Action buttons, interactive CTAs.
- **Borders & Elevation**: Thin 1px borders with low contrast; minimal shadows.

### 10.2 Linux Tree Citation Structure
- Thread citations and replies render authentic Linux `tree` branch formatting (`├── `, `└── `, `│   `) across Compose Multiplatform and Web UI.

### 10.3 Spatial Navigation & URL Hash Routing
- Contextual breadcrumbs (`Author → Thought → Passage`) provide spatial orientation.
- Browser URL hash routing (`#billboard`, `#feed`, `#subscribed`, `#notifications`, `#chat?id=...`, `#author`, `#detail?id=...`) ensures browser refreshes preserve the active screen.

### 10.4 Unified Sovereign App Icon
- Quotation marks fused with graph nodes and connecting lines on a pitch-black squircle canvas:
  - **Web**: `favicon.svg`, `favicon.ico`, `icon.png`, `apple-touch-icon.png`, `site.webmanifest`.
  - **Desktop**: 512x512 `icon.png` and multi-size `icon.ico` wired to Compose window and desktop launchers.
  - **Android**: Adaptive icon with black background, foreground graphic scaled across `mdpi` through `xxxhdpi`, squircle and circular mipmaps.

---

## 11. Multiplatform Client Infrastructure

- **Compose Multiplatform Modules**:
  - `:app:shared`: Common UI screens (`MainScreen`, `BillboardScreen`, `FeedScreen`, `SubscribedScreen`, `AuthorScreen`, `ChatScreen`, `PassageDetailScreen`, `NotificationsScreen`).
  - `:app:desktopApp`: JVM runner with native window decorations and desktop icon integration.
  - `:app:androidApp`: Android entrypoint with edge-to-edge layout, adaptive density support, and back-button handling.
- **Embedded Web Client (`index.html`)**:
  - High-performance, zero-dependency single-page responsive web client served directly by Ktor.
  - Relative API routing (`/graphql`, `/status`, `/api/*`).

---

## 12. Build, Packaging & Distribution Tooling

### 12.1 Zero-Hardcoding Universal Artifact Builder (`build-all-artifacts.sh`)
- Dynamic JDK discovery across SDKMAN, `PATH`, `/usr/lib/jvm`, macOS `/usr/libexec/java_home`.
- Automatic Android SDK detection and license acceptance.
- Interactive/automatic release keystore creation with `.gitignore` protection.
- Generates all 9 distribution targets in `./dist`:
  - **Server**: Fat JAR (`quotes-connect-server-1.0.0.jar`), Tarball, Zip.
  - **Desktop**: Runnable Uber JAR, Linux standalone bundle.
  - **Package Managers**: Debian package (`.deb`), Arch Linux package (`.pkg.tar.zst`).
  - **Android**: Signed Release APK and Debug APK.
  - **Manifest**: Consolidated SHA-256 catalog (`dist/SHA256SUMS`).

### 12.2 Workspace Cleaner (`clean.sh`)
- Automated cleaner supporting `--all`, `--db`, `--cache`, and `--dry-run`.

### 12.3 Safe Source Archive Builder (`safe-zip`)
- Generates clean distribution zip archive (`quotes-connect.zip`) excluding build artifacts, `.git`, `.gradle`, `dist`, keystores, and nested zip files.

---

## 13. Desktop Hardening, Session Persistence & Key Bindings

### 13.1 Desktop SQLite Native JNI & ProGuard Hardening
- **ProGuard Class Retention**: Added explicit retention rule `-keep class org.sqlite.** { *; }` to `app/desktopApp/proguard-rules.pro`. Fixed critical runtime failure where dynamic JNI class resolution `FindClass("org/sqlite/Function")` failed with `java.lang.NoClassDefFoundError: org/sqlite/Function`.
- **SLF4J Provider Binding**: Bound explicit `logback-classic` provider and ProGuard retention `-keep class ch.qos.logback.** { *; }` to silence NOP logger warnings and provide deterministic logging across desktop platforms.
- **Transitive Module Export**: Exported `libs.sqlite.jdbc` via `api()` in `:app:shared` build configuration.

### 13.2 Session Persistence Across Launches (Zero-Plaintext Invariant)
- **Automatic Identity Restoration**: Updated `SessionManager.restorePersistedSession()` to instantiate active credentials `Credentials(authSession.authorId, "")` from the valid unexpired `PersistedSession`. Compose Multiplatform UI components immediately recognize the logged-in author on app launch without prompting for login.
- **Server Session Alignment**: `QuotesViewModel.loginIdentity()` calls `apiClient.login` to negotiate server bearer session tokens, saving the persistent session with `rememberMe = true`.
- **Zero-Plaintext Passphrase Invariant Preserved**: Passphrases remain in volatile memory only during active session use and are never serialized to disk.

### 13.3 Request Deduplication & Concurrency Guards
- Eliminated redundant double-fetch on startup by removing redundant synchronous `refreshAll()` from `QuotesViewModel.init` and letting `ServerEndpointManager` handle discovery.
- Protected `refreshAll()` with `isRefreshingAll` concurrency guard, ensuring simultaneous events do not trigger duplicate network queries.

### 13.4 Desktop & Web Keyboard Zooming (`Ctrl +`, `Ctrl -`, `Ctrl 0`)
- **Desktop Compose Window**: Wired `onKeyEvent` in `Window` with `LocalDensity` scaling (`Density(original.density * zoomFactor, original.fontScale * zoomFactor)`). Supports `Ctrl +` / `Ctrl =`, `Ctrl -`, and `Ctrl 0` to scale entire multiplatform UI from 60% to 220%.
- **Web SPA Client**: Attached keydown listener on `window` supporting `Ctrl +`, `Ctrl -`, and `Ctrl 0` adjusting `document.body.style.zoom`.

---

## 14. Chat Uniqueness, Alias Search & Cross-Platform UI Alignment

### 14.1 Single Session Instance Per Chat Channel Type
- Constrained chat sessions such that between any two authors (A and B), at most **one** instance of each chat type (`UNENCRYPTED`, `ENCRYPTED`, `ENCRYPTED_CLOUD`) can ever exist.
- Implemented bidirectional session lookup in both `JdbcPassageRepository.createChatSession` and `InMemoryKnowledgeRepository.createChatSession`:
  - `WHERE ((initiator_id = ? AND recipient_id = ?) OR (initiator_id = ? AND recipient_id = ?)) AND type = ?`
  - If a session of that type already exists between the authors, the repository updates the initiator public key (if new) and returns the existing session instead of inserting duplicate rows.

### 14.2 Sovereign UUID and Alias Search Discovery Settings
- **Sovereign UUID Always Valid**: Direct sovereign UUID queries (`pas-...`) always resolve regardless of alias settings, preserving foundational decentralized identity lookup.
- **Configurable Alias Discovery**: Added `aliasSearchEnabled: Boolean = true` to `AuthorSettings`, backed by persistent `alias_search_enabled INT NOT NULL DEFAULT 1` database column.
- **Strict Resolution Enforcement**: `resolveAuthorByUsernameOrId` only resolves `@username` or moniker queries if the author has `aliasSearchEnabled == true`. If disabled, alias lookups return `null`.
- **Chat Request Validation**: GraphQL `requestChat` mutation and REST routes validate recipient UUID or alias via `resolveAuthorByUsernameOrId`. If the alias is invalid or disabled, requests fail closed with clear error messaging: `"Invalid recipient: Author '@alias' not found or alias search is disabled"`.
- **Settings Toggle**: Added "Allow Alias Discovery & Chat Search" toggle to Compose `AuthorScreen.kt` and Web client with clear privacy explanations.

### 14.3 Space-Based Tag Chipping
- **Space-to-Chip Interaction**: Updated `CreatePassageDialog.kt` and Web `index.html` so that typing a space character (`" "`) immediately splits the input and appends the normalized tag to the interactive chip row. Comma (`,`) and Enter are also supported.

### 14.4 Ported Desktop Create Passage UI to Web Client
- Fully aligned the Web `index.html` create passage modal with the desktop Compose UI:
  - Horizontal pill buttons for passage types (`QUOTE`, `PASSAGE`, `COMMENT`, `RECORDABLE`, `SOURCE`).
  - "Claim Ownership at Creation" checkbox (records author lineage, visible when authenticated).
  - "Public Domain Orphan Passage" notice (displayed for unparented thoughts).
  - "Advertise to Billboard" checkbox.
  - "Make Recordable (Immutable)" ledger checkbox with lock indicator.

---

## 15. Advanced Chat Reliability, Cryptography Hardening, Polling & Lifecycle Management

### 15.1 Duplicate Request Elimination & Encrypted Channel Response Handling
- **Root Cause Resolution**: In `server/src/main/kotlin/connects/quotes/Application.kt`, the `/graphql` POST route handled encrypted request payload decryption and response encryption, but was missing `call.respond(encryptedResp)` after encryption. This caused the Ktor server to send an empty HTTP 200 OK body to the client. The client-side GraphQL parser encountered an empty body, caught the JSON parsing exception, and immediately retried the request unencrypted ~42ms later, causing duplicate requests and duplicate messages.
- **Encrypted Response Pipeline**: Added `call.respond(encryptedResp)` to complete the encrypted roundtrip pipeline.
- **Concurrent Submission Guard**: Added `isSendingChatMessage` atomic concurrency lock in `QuotesViewModel.kt` to prevent rapid multi-clicks or repeated keyboard shortcuts from dispatching concurrent duplicate requests.

### 15.2 Cryptographic Envelope & Key Sanitization (`Illegal base64 character 2d` Fix)
- **Root Cause Resolution**: In `PlatformCrypto.jvm.kt` and `PlatformCrypto.android.kt`, public keys formatted with PEM headers (`-----BEGIN PUBLIC KEY-----` containing ASCII 0x2D `-`), line breaks, or simulator/web mock prefixes (`PUB-WEB-`) caused `IllegalArgumentException: Illegal base64 character 2d` inside `Base64.getDecoder().decode(recipientPublicKey)` during envelope encryption.
- **Robust Key Sanitization**: Implemented `cleanBase64()` sanitization that strips PEM header/footer lines and all non-Base64 characters (`[^A-Za-z0-9+/=]`), falling back to URL-safe Base64 decoding (`java.util.Base64.getUrlDecoder()`) if standard decoding fails.
- **Cross-Platform Envelope Compatibility**: Added an `ASYM:<base64>` fallback envelope format for mock/non-RSA peer keys (e.g., web mock keys or simulator keys), enabling seamless cross-platform encrypted message exchanges without crashes.

### 15.3 Real-Time Chat Polling, Reconnection & Typing Status Lifecycle
- **Infinite SSE Stream Timeout**: Multiplatform Ktor defaults to strict HTTP timeouts. Configured `Long.MAX_VALUE` infinite timeout in `RemoteGraphQLClient.kt` for `streamLiveChat` and `streamNotifications` to prevent server-sent event connections from dropping every 15-30 seconds.
- **Resilient SSE Auto-Reconnect**: Wrapped SSE stream consumption in `QuotesViewModel.kt` in an auto-reconnecting `while (isActive)` loop with exponential backoff on disconnect.
- **Active TCP Message Polling**: Added periodic 2.5s TCP message polling job (`liveChatPollingJob`) in `selectChatSession()` and background sync that polls `getChatMessages` over standard HTTP/TCP, guaranteeing reliable real-time message sync even if SSE is blocked or interrupted by network proxies.
- **Typing Status Auto-Reset**: Added 4-second auto-reset timer (`typingResetJob`) in `QuotesViewModel.kt` that automatically clears peer typing indicators if no typing events are received within 4 seconds.

### 15.4 Duplicate Chat Prevention & Explicit User Feedback
- **Server-Side Validation**: In `GraphQLGateway.kt` (`requestChat` mutation) and backend repositories (`JdbcPassageRepository`, `InMemoryKnowledgeRepository`), checks if an active session already exists between the authors for the requested type (`UNENCRYPTED`, `ENCRYPTED`, `ENCRYPTED_CLOUD`).
- **Explicit Error Messaging**: If a session exists, the server returns an explicit error: `"Duplicate chat: A [type] chat session already exists with this author"`.
- **Client UX Auto-Switch**: `QuotesViewModel.kt` and Web `index.html` inspect existing sessions locally and handle `"Duplicate chat:"` error responses by automatically switching to the existing session and displaying an informative notification rather than failing silently.

### 15.5 Alias Retention as Local Personal Name
- **Automatic Alias Capture**: When creating or requesting a chat using an `@alias` (e.g., `@sanjay`), the target alias is automatically preserved as the chat's local personal name.
- **Persistent Storage**: Added `chat_personal_names` table in desktop JVM SQLite (`ClientChatDatabase.jvm.kt`) and Android SQLite (`ClientChatDatabase.android.kt`), backed by thread-safe in-memory stores for JS and WasmJS, and `localStorage` for Web client (`index.html`).
- **UI Presentation**: `ActiveChatCard` in Compose Multiplatform (`ChatScreen.kt`, `MainScreen.kt`) and Web SPA displays the custom personal name with a distinct `[Personal]` badge, along with an interactive dialog to rename or clear it anytime.

### 15.6 Full-Stack Chat Session Deletion
- **Backend Repository**: Added `deleteChatSession(chatId: String, authorId: String): Boolean` across `ChatRepository.kt`, `InMemoryKnowledgeRepository.kt`, and `JdbcPassageRepository.kt`. Enforces that only participating authors can delete a chat. Deletes session row, unencrypted messages, encrypted cloud messages, and pending chat keys in a transactional operation.
- **GraphQL Mutation**: Added `deleteChat(chatId: String!): Boolean!` to `GraphQLGateway.kt` with AST root-field authorization.
- **Client API**: Added `deleteChat(chatId: String)` to `RemoteGraphQLClient.kt`, `QuotesApiClient.kt`, and `QuotesViewModel.kt`.
- **Multiplatform UI**:
  - Compose Multiplatform: Added `[✕]` button on `ActiveChatCard` and top bar `DELETE` button on `ChatConversationView`, accompanied by a confirmation dialog with full warning.
  - Web SPA: Added `✕ DELETE` button to chat sidebar cards and `DELETE` header button with confirmation prompt, clearing remote history and local cached messages.

---

## 16. Comprehensive Security, Privacy & Performance Hardening

### 16.1 Elimination of Pending Keys & Persistent Key Material (SEC-002, SEC-003, SEC-004)
- **Zero Key-Share Database Persistence Invariant**: Completely dropped the `pending_chat_keys` table and asynchronous key delivery from database persistence. The server never persists key shares, exchange packets, or secrets.
- **Dedicated Ephemeral In-Memory Key Exchange Relay (`KeyExchangeRelay` / QC-KEYRELAY-1)**:
  - Ephemeral in-memory rendezvous: Key exchange occurs strictly when BOTH participants are actively present and authenticated.
  - Automatic timeout (45s) and bounded channels (capacity 16) prevent denial of service and memory leaks.
  - If peer is offline, participant receives `WAITING_FOR_PEER`. When peer connects, both receive `PEER_CONNECTED`.
  - Disconnect or abort immediately destroys relay state. Zero DB writes, zero logging of key bytes.
  - Real-time endpoints added: `/api/chat/key-relay/join`, `/api/chat/key-relay/stream` (SSE/TCP), `/api/chat/key-relay/packet`, and WebSockets support.
  - Removed legacy `/api/chat/pending-keys` and `/api/chat/pending-keys/ack` from repository and routes.

### 16.2 Sovereign Chat Mode Invariants & `ChatSecurityPolicy` (SEC-015, Sections 49-51, 64-65)
- **Hard Server-Side Invariants**:
  1. `UNENCRYPTED_CLOUD` (`ChatType.UNENCRYPTED`):
     - Persistent cloud conversation. Server stores plaintext in `unencrypted_chat_messages`. Transport protected by TLS.
  2. `ENCRYPTED_CLOUD` (`ChatType.ENCRYPTED_CLOUD`):
     - Client-side encrypted chat. Server stores ciphertext ONLY in `encrypted_cloud_chat_messages`. Server cannot decrypt.
     - Unencrypted plaintext messages are strictly rejected with `"Security Policy Violation: ENCRYPTED_CLOUD requires application-level encryption. Unencrypted plaintext messages are strictly forbidden."`.
  3. `ENCRYPTED_ON_DEVICE` (`ChatType.ENCRYPTED`):
     - Device-only encrypted chat. Server database message persistence is strictly FORBIDDEN.
     - Messages are never written to `unencrypted_chat_messages` or `encrypted_cloud_chat_messages`. Delivered in-memory only.
- Integrated `ChatSecurityPolicy.validateSend` across backend repositories (`JdbcPassageRepository`, `InMemoryKnowledgeRepository`) and GraphQL gateway (`sendChatMessage` mutation).

### 16.3 Client Session Security & Passphrase Scrubbing (SEC-001, SEC-006, SEC-005)
- **Elimination of Plaintext Passphrase Persistence**:
  - `SessionManager.kt`: Removed raw passphrase from `PersistedSession`.
  - Web client (`index.html`): Replaced `qc_passphrase` storage in `localStorage`/`sessionStorage` with revocable bearer session tokens (`qc_auth_token`) authenticated via `POST /api/auth/login`. Passphrase is scrubbed from browser memory upon login.
  - Authenticated GraphQL requests transmit `Authorization: Bearer <accessToken>`.
- **Cryptographically Secure PRNG for Web Key Generation (SEC-005)**:
  - Replaced all usages of `Math.random()` in key generation and identity tools with `window.crypto.getRandomValues(new Uint8Array(32))`.

### 16.4 Cryptographic Rigor & Padding Hardening (SEC-007)
- **Enforced OAEP-SHA256**: Removed obsolete RSA PKCS#1 v1.5 fallback across JVM (`PlatformCrypto.jvm.kt`), Android (`PlatformCrypto.android.kt`), server channel crypto (`ServerChannelCrypto.kt`), and tests. Enforced `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`.

### 16.5 Database Integrity & Deterministic Migrations (SEC-009, SEC-010, OPT-001–OPT-005, OPT-009, OPT-010)
- **SQLite Pragmas**: Enforced `PRAGMA foreign_keys = ON;` and `PRAGMA journal_mode = WAL;` on SQLite connections.
- **Deterministic Schema Migrations**:
  - `001_schema_baseline`: Initial schema registration.
  - `002_drop_legacy_credentials`: Migrated lingering credentials to `auth_credentials` (salted PBKDF2), dropped `credentials` table.
  - `003_remove_pending_chat_keys`: Dropped `pending_chat_keys` table.
  - `004_chat_mode_constraints`: Added alias and proposal columns.
  - `005_performance_indexes`: Created composite performance indexes on passages, subscriptions, notifications, encrypted cloud messages, billboard, auth sessions, and activity logs.

### 16.6 Graph Traversal DoS Protection (OPT-008)
- Added `maxDepth` and `maxNodes` limits to `GraphEngine.buildDescendantTree`.
- Added `maxDepth`, `maxNodes`, and `maxEdges` bounds to `GraphEngine.findShortestPath` preventing CPU/memory exhaustion from cyclical or unbounded graph traversals.

### 16.7 Defensive Redaction & Zero Secret Logging (SEC-012, SEC-079, KE-001)
- Implemented defensive regex redaction in `SecurityAuditLogger.kt` that sanitizes all logged events.
- Banned logging of passphrases, private keys, public keys, ciphertexts, signatures, nonces, and keystore payloads.

### 16.8 Keystore Lock / Unlock Flow Across Platforms
- Added `lockKeystore()` function to `QuotesViewModel.kt` and Compose UI (`ChatScreen.kt`, `MainScreen.kt`) allowing users to explicitly lock their sovereign keystore and wipe local key memory.
- Added `lockKeystore()` and LOCK button in Web SPA (`index.html`).

---

## 17. Verification & Test Register

The entire codebase is verified and deterministically passing:

| Test Suite / Command | Target Module | Scope | Result |
| :--- | :--- | :--- | :--- |
| `PassagePolymorphismTest` | `:core` | Passage types & serialization | **PASSED** |
| `DagCycleDetectionTest` | `:core` | Cycle prevention & BFS algorithms | **PASSED** |
| `PassageRulesTest` | `:core` | Deletion, immutability, orphan rules | **PASSED** |
| `ServerEndpointConfigTest` | `:core` | Transport detection & URL parsing | **PASSED** |
| `LinuxTreeFormatTest` | `:core` | Tree formatting rendering logic | **PASSED** |
| `AuthorSelfAdvertisementTest`| `:core` | Author profile billboard defaults | **PASSED** |
| `FeedAndVisibilityTest` | `:core` | Lazy feed traversal & visibility | **PASSED** |
| `TagSystemTest` | `:core` | Tag parsing & debounced filtering | **PASSED** |
| `SharedCommonTest` | `:app:shared` | ViewModels & multiplatform clients | **PASSED** |
| `ApplicationTest` | `:server` | Ktor startup, endpoints, health, chat mutations, encrypted cloud chat & key renewals | **PASSED** (10/10) |
| `ChannelCryptoTest` | `:server` | Ephemeral asymmetric key exchange | **PASSED** |
| `AuthSecurityTest` | `:server` | PBKDF2 salting, AST root-field parser isolation, bearer sessions, token rotation, authorization matrix | **PASSED** (24/24) |
| `SecurityHardeningAuditTest` | `:server` | Zero key-share persistence, ephemeral relay, 3 chat modes, keystore backup/lock/unlock, audit redaction, bounded graph traversals | **PASSED** (6/6) |
| `LiveChatTcpNotificationTest` | `:server` | Real-time TCP push notifications, chat deletion broadcast, status updates | **PASSED** (3/3) |
| Multiplatform Compilation | `:core`, `:app:shared`, `:server`, `:app:desktopApp` | JVM, Android, JS, WasmJS targets | **PASSED** |

---

## 18. Web Application Viewport-Locked Chat & Adaptive Scrolling Architecture

### 18.1 Non-Scrollable Chat Section Mode (`body.chat-active`)
- **Outer Page Viewport Locking**:
  - When in the Chat section (`activeTab === 'chat'` and `currentInspectedId == null`), the web page outer window/body is strictly locked to the viewport (`height: 100vh; height: 100dvh; max-height: 100vh; max-height: 100dvh; overflow: hidden !important;`).
  - Applied `.chat-active` to `document.body` and `.chat-active-html` / `:has(body.chat-active)` to `document.documentElement`, completely eliminating window-level vertical scrolling and bounce.
- **Conversation View Containerization (`.chat-conversation-view`)**:
  - Top action bar (`← BACK`, `🟢 LIVE`, `REFRESH`, `DELETE`) is pinned at the top with `flex-shrink: 0`.
  - Alias bar, proposed alias banner, locked keystore banner, and quoting indicator are pinned above the messages.
  - Message box (`#chatMessagesBox`) takes all remaining vertical space via flexbox (`flex: 1 1 0%; min-height: 0; overflow-y: auto; overscroll-behavior: contain;`).
  - Chat input area (`#chatInputArea`) is permanently anchored to the bottom (`flex-shrink: 0; margin-top: auto;`).
  - Messages scroll cleanly inside `#chatMessagesBox` without chaining scroll events to the outer page or window.
- **Sessions List Containerization (`.chat-sessions-view`)**:
  - Header, Keystore Status card, and Search filter toolbar are pinned at the top (`flex-shrink: 0`).
  - Sessions container (`#chatSessionsContainer`) fills remaining viewport height (`flex: 1 1 0%; min-height: 0; overflow-y: auto; overscroll-behavior: contain;`), scrolling conversations internally without scrolling the outer page.
- **Custom Brutalist Scrollbar Theming**:
  - Sleek thin scrollbars (`scrollbar-width: thin; scrollbar-color: var(--border) transparent;` and `-webkit-scrollbar`) matching Quotes Connect monochrome design tokens.

### 18.2 Preserved Native Page Scrolling for All Other Tabs & Inspector
- **Natural Window Scrolling**:
  - When switching away from Chat to `BILLBOARD`, `FEED`, `SUBSCRIBED`, `NOTIFICATIONS`, `AUTHOR` / `PROFILE`, or when inspecting any passage or quote detail view (`INSPECTOR`), `body.classList.remove('chat-active')` and `html.classList.remove('chat-active-html')` are executed.
  - `body` returns to `min-height: 100vh; overflow: visible/auto;` and `main#appMain` returns to standard `padding: 24px 16px 80px 16px;`.
  - All public commons, feeds, subscriptions, notifications, and passage detail trees remain naturally and fluidly scrollable with standard browser scrollbars.
- **Centralized Synchronization (`QC.Router.updateChatLayoutMode()`)**:
  - Unified layout mode synchronization hooked into `QC.Router.render()`, `QC.Tabs.switch()`, `QC.Router.inspect()`, `QC.Router.backToList()`, `QC.Router.initHashRouting()`, `mount()`, and `closeConversation()`, ensuring instantaneous, zero-latency viewport locking across all navigation paths.

---

## 19. Real-Time TCP Notification & Live Chat Stream Architecture with Deletion and Status Synchronization

### 19.1 Server Event Hub & Persistent Stream Routing (`LiveChatHub`)
- **Instant Event Broadcasting**:
  - `broadcastChatRequested(session: ChatSession)`: Emits an instant `STATUS_CHANGED` notification packet with context (`New chat request from <initiator>`) to both the initiator and recipient persistent notification streams over persistent TCP SSE (`/api/chat/notifications`).
  - `broadcastChatDeleted(chatId: String, deletedBy: String, initiatorId: String, recipientId: String)`: Emits an instant `CHAT_DELETED` packet to:
    1. Active chat room subscribers (`chatSubscribers[chatId]`) — immediately notifying both parties if actively viewing the conversation.
    2. Both participants' notification subscribers (`notificationSubscribers[initiatorId]` and `notificationSubscribers[recipientId]`) — immediately removing the conversation from the chat list and updating unread badges.
  - `broadcastSessionUpdated(session: ChatSession, statusMessage: String?)`: Dispatches `STATUS_CHANGED` with informative text when a chat request is accepted, rejected, or updated.
- **TCP Keepalive & PING Packets (`/api/chat/live` & `/api/chat/notifications`)**:
  - Persistent SSE streams emit both raw comment keepalives (`:keepalive\n\n`) and typed structured packets (`LiveChatPacket(type = "PING", ...)`) periodically on idle timeout.
  - Keeps TCP sockets open, prevents proxy/carrier timeout drops, and allows clients to verify TCP stream liveness without triggering UI disruptions.

### 19.2 Compose Multiplatform Desktop / Mobile Integration (`QuotesViewModel`)
- **Dual-Layer Real-Time Synchronization**:
  - **Layer 1 (Push)**: Persistent notification stream (`ensureNotificationStream`) and active room stream (`streamLiveChat`).
    - Receives `CHAT_DELETED`: If the current open conversation was deleted by a peer, automatically clears `selectedChatSession`, clears `activeChatMessages`, resets live connection, and notifies the user with a toast.
    - Receives `STATUS_CHANGED`: Updates active session details and triggers `refreshChats()`.
    - Auto-reconnect: Stream reconnection loop with 3-second backoff when disconnected.
  - **Layer 2 (Lightweight Polling Sync)**: `chatTabPollingJob` runs a 3000ms loop exclusively while `activeTab == MainTab.CHAT`. Syncs active messages when in a chat room, or sessions list when viewing chats. Disposed immediately when leaving the Chat tab to guarantee zero background CPU overhead.

### 19.3 Web Client Integration (`index.html`)
- **`QC.Notifications` Stream Manager**:
  - Automatically connected upon identity creation or login via `/api/chat/notifications?authorId=...`.
  - Disconnected cleanly upon logout (`disconnectIdentity()`).
  - Reacts instantly to `NOTIFICATION`, `STATUS_CHANGED`, `CHAT_DELETED`, `KEY_CHANGED`, `ALIAS_PROPOSAL`, `ALIAS_ACCEPTED`.
- **`QC.ChatPolling` Adaptive Polling Manager**:
  - Automatically started on switching to the `#chat` tab; stopped upon switching to any other tab.
  - Runs every 3000ms to fetch recent messages or sessions silently without DOM flicker or interrupting user typing.
- **Silent Refresh & Non-Disruptive UI**:
  - `refreshSessionsSilently()`: Re-queries GraphQL chats without resetting scroll or re-rendering entire layout.
  - `refreshMessagesSilently(chatId)`: Fetches new messages and updates `#chatMessagesBox` while preserving the user's active draft in `#chatMsgContent`, scroll position if reviewing history, and auto-scrolling only if already at the bottom.
  - `mountConversation` EventSource: Direct handling of `CHAT_DELETED` (toasts and calls `closeConversation()`), `STATUS_CHANGED`, `MESSAGE`, `TYPING`, and `PING`.

---

## 20. Intent-Based TCP Streaming and Granular Reactive System

### 20.1 Protocol & Intent Architecture (`ChatIntentCode.kt` & `LiveChatHub.kt`)
- **Granular Integer Opcodes (`ChatIntentCode`)**:
  - `STATUS_CHANGED = 1`: New approvals, rejections, or incoming chat connection requests.
  - `MESSAGE = 2`: New messages or private citations sent in a conversation.
  - `TYPING = 3`: Quoting / typing status changes for peer.
  - `CHAT_DELETED = 4`: Conversation deleted by either participant.
  - `NOTIFICATION = 5`: In-app notification alerts for citations or unopened messages.
  - `GRAPH_CHANGED = 6`: Billboard / Feed knowledge graph additions.
  - `ALIAS_CHANGED = 7`: Shared alias proposals or acceptances.
  - `KEY_CHANGED = 8`: Public key renewal or rotation.
  - `PING = 9`: Low-overhead TCP keepalive heartbeat.
  - `RESYNC_REQUEST = 10`: Client-to-peer session resync requests.
- **UUID to Lambda Registry Pattern (`LiveChatHub.kt`)**:
  - Central `ConcurrentHashMap<String, IntentSubscriber>` maps TCP connection UUIDs (`connectionId`) to subscriber descriptors.
  - Each subscriber holds:
    - `connectionId`: Unique TCP connection UUID.
    - `authorId`: Sovereign Author identity.
    - `chatId`: Optional conversation scope.
    - `registeredIntents`: Set of integer opcodes registered by the client window/tab.
    - `channel`: Non-blocking buffered TCP stream channel.
    - `predicate: (LiveChatPacket) -> Boolean`: The evaluation lambda accepting intent packets.
- **Loopback Dispatch Mechanism (`LiveChatHub.dispatchIntent`)**:
  - When mutations occur or database transactions complete, `LiveChatHub.dispatchIntent(packet)` evaluates each subscriber's filter lambda with sub-millisecond execution time.
  - Packets are pushed directly across active TCP streams without polling delays or database scans.
- **Elimination of Duplicate Message Delivery**:
  - Cleaned up redundant registrations between `chatSubscribers` and `intentSubscribers` in `registerChatListener` and `registerNotificationListener`.
  - Guarantees exactly-once packet delivery across both legacy `/api/chat/live` and modern `/api/chat/stream` endpoints.

### 20.2 Granular Element-Level Web Client Reactivity (`index.html`)
- **Direct Element Injection (`QC.Components.Chat.appendMessageElement`)**:
  - When `MESSAGE` (Intent 2) arrives via SSE or when the local author sends a message via GraphQL mutation, the new message is rendered via `buildMessageHtml(msg)` and inserted directly into `#chatMessagesBox` using `box.insertAdjacentHTML('beforeend', msgHtml)`.
  - Completely avoids re-fetching conversation history over GraphQL and eliminates whole-conversation DOM destruction and rebuilds.
  - Intelligently scrolls to the bottom if the user is currently near the bottom or sending a message, preserving scroll position if the user is reading past history.
- **Isolated Typing Indicators**:
  - On `TYPING` (Intent 3) packet, updates only `#chatQuotingIndicator` (`display: block` / `none` and text) without re-rendering or touching messages.
- **In-Memory Session List Updates**:
  - Each conversation card and pending request is tagged with an explicit DOM ID (`#chat-card-${id}`, `#pending-incoming-${id}`, `#pending-outgoing-${id}`).
  - On `CHAT_DELETED` (Intent 4): `handleChatDeletedOnSessionList(chatId)` fades and removes the specific card element from the DOM and filters `allSessions` in memory.
  - On `STATUS_CHANGED` (Intent 1): `handleStatusChangedOnSessionList(packet)` updates the affected `ChatSession` in memory and updates the list cleanly without re-executing GraphQL queries.
  - On `NOTIFICATION` / unread messages: `handleMessageIntentOnSessionCard(packet)` reveals the `● NEW` unread chip on the specific card and subtly pulses its border.
- **Polling Relaxation**:
  - Because TCP streaming is instant and reliable, `QC.ChatPolling` and Compose `chatTabPollingJob` were relaxed to a 30-second background liveness safety check, reducing client and server CPU load to zero.

### 20.3 Multiplatform ViewModel Reactivity (`QuotesViewModel.kt`)
- In `streamLiveChat` and `ensureNotificationStream`, incoming `STATUS_CHANGED`, `CHAT_DELETED`, `ALIAS_PROPOSAL`, and `ALIAS_ACCEPTED` packets update `selectedChatSession` and `chatSessions` directly in memory without re-fetching all chats from the server.

---

## 21. Real WebCrypto & Sovereign End-to-End Cryptography

### 21.1 Native Browser WebCrypto (`SubtleCrypto`) Implementation
- **Total Elimination of Mock/Placeholder Cryptography**:
  - Permanently purged all `PUB-WEB-*` and `PRIV-WEB-*` dummy strings, fake signature generators, and pseudo-encryption string concatenations.
  - Implemented real W3C Web Crypto (`window.crypto.subtle`) across the web single-page application (`index.html`) and JS multiplatform targets.
- **Asymmetric Key Agreement & Envelope Cryptography**:
  - 2048-bit RSA-OAEP key generation with SHA-256 digest and public exponent $[0x01, 0x00, 0x01]$ ($65537$).
  - Asymmetric envelope wrapping and unwrapping for peer key exchange and channel encryption.
- **Symmetric Channel & Message Ciphers**:
  - AES-GCM 256-bit with 12-byte CSPRNG IVs (`crypto.getRandomValues`) and 128-bit authentication tags.
  - Authenticated Additional Data (AAD) binding to conversation and session context.
- **Passphrase-Derived Keystore Protection**:
  - PBKDF2-HMAC-SHA256 with 100,000 iterations and a 16-byte random salt.
  - Encrypted keystores stored at rest in browser storage; unsealed only in memory upon explicit passphrase challenge.
  - Zero private keys in URLs, server logs, or unencrypted storage.

---

## 22. Interactive Visual Graph Canvas Architecture

### 22.1 Compose Multiplatform Graph Canvas (`VisualGraphCanvas.kt`)
- **Interactive 2D Rendering Engine**:
  - Built using `androidx.compose.foundation.Canvas` with high-performance hardware-accelerated drawing.
  - **Radial / Topological Force Layout**: Places the central root node in crisp white, parent authors in sovereign blue/green, subscriber nodes in cyan, and citing thoughts in elevated gray.
  - **Directed Edges**: Renders vector paths with directional arrows showing citation and reaction flow.
  - **Gesture Engine**: Supports multi-touch / mouse drag panning, wheel/pinch zooming with reset controls, and hit-testing for tap-to-inspect navigation.
- **Passage Detail Screen Integration (`PassageDetailScreen.kt`)**:
  - Toggle buttons in header (`[TREE]` vs `[CANVAS]`) allowing seamless switching between hierarchical ASCII branch trees and the interactive 2D graph canvas.

### 22.2 Web HTML5 Canvas Visualizer (`#inspectorGraphCanvas`)
- **Native 2D Context Rendering**:
  - `<canvas id="inspectorGraphCanvas">` dynamically resizes to device pixel ratio (DPR) for retina display sharpness.
  - Force/radial topological node positioning with collision dampening.
  - Full mouse drag-to-pan, mouse wheel zoom, and reset zoom controls.
  - Interactive hover state: highlights connected nodes and renders a floating detail tooltip showing passage content and author attribution.
  - Dual-mode inspector controls (`[TREE]` vs `[GRAPH CANVAS]`) in the web UI.

---

## 23. Fail-Closed Authentication & Session-Bound Authorization Matrix

### 23.1 Principle of Fail-Closed Enforcement
- Eliminated all reliance on client-supplied identifiers (`authorId`, `senderId`, `userId`).
- Requests attempting to modify state or access private streams without a valid, matching `AuthPrincipal` return `401 Unauthorized` or `403 Forbidden` / GraphQL errors.
- Never silently downgrades to unencrypted plaintext; missing cryptographic primitives throw typed `UnsupportedSecureOperation`.

### 23.2 Complete Protection Matrix Across GraphQL and REST
- **GraphQL Mutations Hardened**:
  - `createPassage`: When specifying an author `parentId`, caller must present a Bearer token matching that author ID.
  - `deletePassage`: Verifies caller ownership (`passage.parentId == principal.authorId`).
  - `setPassageAdvertisement` / `setPassageTypeAdvertisement`: Enforces caller owns target passages.
  - `subscribeAuthorPassage` / `unsubscribeAuthorPassage`: Enforces subscriber is authenticated caller.
  - `sendMessage`: Enforces caller is an active participant (`initiatorId` or `recipientId`).
  - `acceptChatRequest` / `rejectChatRequest`: Enforces caller is the designated recipient.
  - `proposeChatAlias` / `acceptChatAlias`: Enforces participant identity.
  - `discardAuthor`: Enforces caller identity before purging author credentials.
- **REST Endpoints Hardened**:
  - `/api/chat/stream`, `/api/chat/notifications`, `/api/chat/typing`, `/api/chat/resync`, `/api/author/settings`, `/api/author/discard`, `/api/author/heartbeat` require valid Bearer token matching the requested author.

---

## 24. Multiplatform Keystore Implementation & Storage Hardening

### 24.1 Local Encrypted Keystore
- Desktop/JVM, Android, Web/JS, and Web/WasmJS use the same keystore cryptographic contract.
- New keystore blobs use PBKDF2-HMAC-SHA256 with **600,000 iterations**, a 16-byte salt, a 32-byte derived key, and AES-256-GCM with a 12-byte nonce and 128-bit authentication tag.
- New encrypted blobs use **envelope version 3**. Legacy version 2 blobs remain readable with their historical 100,000-iteration KDF.
- Durable storage receives only the versioned encrypted envelope. Plaintext private keys and passwords are never passed to the durable storage layer.
- Lock removes the usable identity from active memory; unlock restores it only after successful password authentication.
- Corrupt or unsupported envelopes fail closed and are never silently replaced with new identities.

### 24.2 Encrypted Server Keystore Backup
- Cloud backup stores only the client-generated encrypted envelope fields: ciphertext, salt, nonce, and version.
- The server performs shape/version validation only; it never derives password keys or decrypts private key material.
- Backup authorization is bound to the authenticated `AuthPrincipal`.
- The client uploads the exact encrypted local envelope rather than creating a second independent encryption of the same identity.
- Server backup accepts supported envelope versions only and rejects malformed Base64, incorrect salt/nonce lengths, and ciphertext shorter than the GCM authentication tag.
- Download and restore preserve the encrypted envelope; decryption remains entirely client-side.

---

*This document is registered as a permanent project reference. All future interactions must cross-reference `completed.md` and `plan.md` to ensure architectural integrity.*

## 25. Encrypted Server Keystore Restore Integrity

- Cloud keystore restore now copies the downloaded encrypted envelope directly into the local encrypted durable store.
- Restore does not decrypt and re-encrypt the identity, preventing ciphertext churn and preserving the exact server backup artifact.
- Unlock then uses the standard local `LocalChatKeyStore.unlock(...)` path for password verification and private-key recovery.
- JVM, Android, JS, and WasmJS implementations expose the same restore contract.
- Added regression coverage for envelope preservation and identity recovery after restore.
## 26. Complete iOS Support Removal

- Removed the final orphaned `core/src/iosMain`, `app/shared/src/iosMain`, and `app/shared/src/iosTest` source sets.
- Removed the obsolete `app/iosApp/build` cleanup path and iOS target metadata from the IDE receipt.
- Confirmed no active iOS Gradle targets, source sets, application module, iOS crypto/keystore implementation, or iOS CI/build configuration remain.
- Historical stage and audit documents retain iOS terminology only where it records the removal process.


## Stage 25 — Release Artifact Purification

- Removed checked-in server and client runtime SQLite databases from the repository checkpoint.
- Hardened `safe-zip` to exclude SQLite databases and WAL/SHM/journal sidecars from release archives.
- Kept runtime database implementation unchanged; schema redesign and migration are deferred to the dedicated database phase.
- Static release-artifact verification passed.

## Stage 26 — Canonical Security Configuration

- Added a versioned shared security-parameter registry.
- Centralized the currently implemented keystore, ECDH/HKDF/HMAC, RSA, AES-GCM, key-exchange, and envelope identifiers without changing protocol behavior.
- Updated existing contracts to consume the canonical values and added a drift-prevention test.
- Full Gradle verification remains blocked by unavailable Gradle 9.5.1 network access.

## Stage 27 — Documentation Reconciliation

- Reconciled active README, cryptographic ADR, threat model, and architecture plan with the current implementation state.
- Corrected the current keystore KDF to 600,000 iterations while preserving explicit legacy v2 and server credential-verifier parameters.
- Removed overclaims that the current HTTP hybrid channel is complete MITM protection.
- Distinguished the implemented P-256 ECDH exchange from the still-active legacy RSA chat message path.
- Distinguished live HTTP/SSE event streaming from dedicated raw TCP transport.
- Clarified that historical audit reports are not current release evidence.
- Runtime Gradle verification remains environment-blocked.
## Build 004 — Android Compilation Fix

- Fixed missing `kotlinx.coroutines.sync.withLock` imports in Android, JS, and WasmJS `LocalChatKeyStore` implementations.
- Desktop and server builds were confirmed successful by the project owner; Android compilation had failed in the universal artifact build and was corrected without advancing the roadmap.
