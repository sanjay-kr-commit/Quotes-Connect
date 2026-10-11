# Quotes Connect — Persistent Plan & Architectural Memory

> **Persistent Memory Directive**: This file (`plan.md`) along with `completed.md` are the single sources of truth and persistent memory for the Quotes Connect project. `plan.md` defines the manifesto, design system, and architectural blueprint; `completed.md` maintains the authoritative register of all completed, tested, and hardened subsystems. In all future interactions, the assistant and development team must reference, align with, and update both documents as the system evolves.

---

## 1. Project Manifesto & Core Philosophy

**Quotes Connect** is an **algorithm-free, sovereign, decentralized knowledge network**. 

Modern information platforms rely on opaque engagement algorithms, follower counts, and recommendation filters that incentivize polarization and noise. Quotes Connect replaces this paradigm with a pure **Directed Acyclic Graph (DAG)** of interconnected **Passages**.

### Foundational Principles
1. **Isolated Data is Noise; Linked Data is Knowledge; Connected Knowledge is Wisdom.**
2. **Zero Algorithmic Curation**: No recommendation engines, no engagement tracking, no feed manipulation.
3. **Explicit Subscriptions Only**: A user's personal Feed is built purely from explicit directed subscriptions to nodes in the knowledge graph.
4. **Zero Template / Rough Data**: The system starts with an empty, clean database. All data is authored by genuine users; no mock or pre-seeded content is inserted.
5. **Associative Anonymity & Creator Privacy**: The server maintains a private lineage (`parentId`) for ownership permissions and citation alerts, but this field is strictly excluded from public serializations and GraphQL responses.
6. **Ledger Immutability**: Recordable passages (`RECORDABLE` type or `isRecordable = true`) and any passage cited by a recordable passage become permanent historical citations that can never be deleted.
7. **Mandatory Billboard for Orphans**: Any passage created without an owner (`parentId == null`) is classified as public domain orphan knowledge and is automatically and permanently advertised on the Billboard.

### 1.1 UI / UX Design Language & Interaction Specification (Version 1.0)

**Visual Direction**: Monochrome Editorial + Brutalist Utility + Graph-Native Interface  
**Design Philosophy**: Algorithm-Free Knowledge Graph  
**Target Platforms**: Android, Desktop, Web (JS/WasmJS)

#### The Prime Directive (The One Rule)
> **EVERY INTERACTION MUST EITHER CREATE, REVEAL, MODIFY, OR TRAVERSE A RELATIONSHIP.**

1. Creating a thought creates an object node in the graph.
2. Connecting or citing a thought creates a directed edge (`source -> target`).
3. Opening a passage reveals provenance, parent lineage, and citation trees.
4. Opening an author reveals authorship and sovereign thought repository.
5. Searching reveals navigable objects across thoughts, passages, authors, and tags.
6. Feed and Billboard reveal topological relationships without algorithmic interference.
7. Endpoint configuration reinforces sovereign control over the environment.

#### The 10 Core Design Principles
1. **No Algorithmic Theater**: No "For You", no recommendation feeds, no vanity engagement metrics, no fake popularity indicators, no notification spam, no endless scrolling manipulation. The user chooses the path.
2. **Objects Before Feeds**: Everything is an object (Author, Thought, Passage, Tag, Relationship, Conversation, Identity). Objects are visually identifiable with clear boundaries.
3. **Connections as First-Class UI**: Relationships are displayed naturally throughout the interface (e.g. Linux tree branch formatting `├── `, `└── `). The graph is the product.
4. **Sovereignty Over Identity**: Identity belongs to the author. Terminology favors *"Create Sovereign Identity"* and *"Author Passage"* rather than *"Create account"*.
5. **Quiet Interface**: Visually strong without being visually loud. Pitch-black foundations, crisp white typography, grayscale hierarchy, and large intentional negative space.
6. **Content Has Weight**: Thoughts and passages are substantial intellectual artifacts given generous space, rather than compressed cards drowned in social badges.
7. **Spatial Navigation**: Users always know: *Where am I? How did I get here? What is connected to this?* Contextual breadcrumbs (`Author → Thought → Passage`) provide spatial orientation.
8. **Search as Navigation**: Search is an explicit locator of nodes and tags, not a promoted recommendation funnel.
9. **Anonymous Does Not Mean Powerless**: Anonymous is a valid, fully capable viewing and graph exploration state. Connecting an identity is purely an act of sovereignty.
10. **The UI Should Age Well**: Classic editorial typography and brutalist utilitarian restraint outlast short-lived aesthetic trends.

#### Design Tokens & Architecture
- **Color System**:
  - `BLACK` (`#000000`): Primary background foundation.
  - `SURFACE_01` (`#111111`): Primary cards, containers, panels.
  - `SURFACE_02` (`#181818`): Elevated panels, input fields, active containers.
  - `WHITE` (`#FFFFFF`): Primary text and high-contrast accents.
  - `GRAY_01` (`#B5B5B5`): Secondary text, explanatory subtitles.
  - `GRAY_02` (`#666666`): Borders, outlines, metadata labels.
  - `GRAY_03` (`#333333`): Subtle borders, dividers, disabled states.
  - Subdued Semantics: Success (`#4CAF50`), Warning (`#FFA726`), Error (`#EF5350`), Info (`#42A5F5`).
- **Restrained Corner Radii**:
  - `0px` (`QCRadiusStructural`): Structural surfaces, headers, full-width status bars.
  - `4px` (`QCRadiusInput`): Text inputs, search fields, tags, chips, status pills.
  - `6px` (`QCRadiusCard`): Passage cards, content containers, modals, dialogs.
  - `8px` (`QCRadiusButton`): Action buttons, primary interactive CTAs.
- **Borders & Elevation**: Thin 1px borders with low contrast. Minimal shadows; objects are defined by borders, typography, and spacing.
- **Typography Hierarchy**:
  - *Display*: Bold architectural serif for titles and major headings.
  - *Heading*: Semi-bold serif/sans for card titles and section anchors.
  - *Body*: Readable, calm editorial text for passages and thoughts.
  - *Metadata*: Monospace lowercase/tabular for UUIDs, timestamps, endpoints.
  - *Utility*: Monospace UPPERCASE for navigation tabs, buttons, chips, and system status badges.
- **Microcopy Voice**: Concise, factual, dignified, slightly literary; zero corporate jargon, zero manipulative urgency.

---

## 1.2 Current Architecture Truth (Stage 27 Reconciliation)

The following statements override stale implementation prose elsewhere in this living plan until the dedicated convergence stages complete:

- **Keystore KDF:** current keystore envelope v3 uses PBKDF2-HMAC-SHA256 with **600,000** iterations. Legacy keystore envelope v2 remains readable at 100,000 iterations. Server password verifiers are a separate 100,000-iteration contract.
- **Peer chat cryptography:** the repository contains an authenticated P-256 ECDH + HKDF-SHA-256 exchange protocol, but the legacy RSA message-encryption path is still active. The ECDH session secret is not yet the sole message-encryption path.
- **Server trust:** the HTTP hybrid channel currently obtains its ephemeral RSA public key through an unauthenticated HTTP handshake. It must not be described as complete MITM protection until authenticated server identity is implemented.
- **Live transport:** current live event delivery is HTTP/SSE-oriented. References to “TCP chat streaming” describe historical terminology and must not be interpreted as a dedicated raw TCP transport. The final transport decision remains a later architecture stage.
- **Local chat storage:** plaintext local message persistence remains an open security issue until the dedicated local encrypted-storage stages complete.
- **Runtime verification:** this checkpoint is not compile-verified because Gradle 9.5.1 is unavailable from the configured network source.

Historical implementation entries below are retained as project history unless explicitly rewritten by a later reconciliation stage.

## 2. System Architecture & Module Separation

Every module in the Quotes Connect project is strictly separated by responsibility:

```
Quotes Connect
├── core/                  # Pure Kotlin Multiplatform domain & algorithms (No UI, No Server)
│   ├── model/             # Polymorphic Passage entities, Id, BillboardItem, Credentials
│   ├── graph/             # DagValidator, GraphEngine (BFS shortest path & trees)
│   ├── usecase/           # Create, Delete, Subscribe, Traverse, ToggleAdvertisement
│   ├── repository/        # Abstract repository contracts & InMemoryKnowledgeRepository
│   └── config/            # ServerEndpointConfig & ServerEndpointManager (Shared connection contract)
│
├── server/                # Dedicated Ktor Netty backend & web host
│   ├── Application.kt     # Server bootstrap, LAN/Local IP discovery, routing
│   ├── config/            # ServerConfig (Port, host, DB mode)
│   ├── database/          # ServerDatabase & JdbcPassageRepository (SQLite & MySQL)
│   ├── graphql/           # GraphQLGateway, AST parsing, resolvers, GraphiQL explorer
│   └── resources/web/     # Zero-dependency, responsive single-page web UI (index.html)
│
├── app/
│   ├── shared/            # Compose Multiplatform shared UI and ViewModels
│   │   ├── client/        # QuotesApiClient & RemoteGraphQLClient
│   │   ├── viewmodel/     # QuotesViewModel (StateFlow, connection lifecycle)
│   │   └── ui/            # Screens (Billboard, Feed, Detail, Notifications) & Dialogs
│   ├── desktopApp/        # Compose for Desktop (JVM entrypoint)
│   ├── androidApp/        # Native Android wrapper hosting Compose shared UI
│   ├── webApp/            # Kotlin/Wasm & Kotlin/JS browser entrypoints
```

---

## 3. Communication Architecture & Endpoint Contract

### Dual Protocol Modes: HTTP and HTTPS
The server supports two protocol modes dynamically configured via environment variables:
- **HTTP Mode (Default)**: Runs plain HTTP on port `8080` (or `PORT` env var).
- **HTTPS Mode (Secure TLS/SSL)**: Runs TLS encrypted HTTP/1.1 and HTTP/2 on port `8443` (or `PORT`/`SSL_PORT` env var).
- **Environment Variables**:
  - `SERVER_PROTOCOL=https` or `http` (also aliases `PROTOCOL=https`, `SSL_ENABLED=true`, `SERVER_SSL_ENABLED=true`, `HTTPS_ENABLED=true`).
  - `PORT=8443` / `PORT=8080`: Configures the active listening port (defaults: 8443 for HTTPS, 8080 for HTTP).
  - `SSL_KEYSTORE_PATH`: Optional path to custom PKCS12 / JKS keystore.
  - `SSL_KEYSTORE_PASSWORD`: Keystore password (default: `quotesconnect`).
  - `SSL_KEY_ALIAS`: Key alias in the keystore (default: `quotesconnect`).
  - `SSL_KEY_PASSWORD`: Private key password (default: `quotesconnect`).
- **Zero-Friction Automatic Self-Signed Developer Certificate**:
  - In HTTPS mode, if no custom keystore path is provided, the server automatically generates and caches a secure self-signed developer certificate using Ktor TLS tools (`.quotes_connect/server_dev_keystore.jks`). No manual `keytool` orchestration is needed.

### The Embedded Server Address Model
- **The Server knows and embeds its address**: When `:server` starts, it binds to `0.0.0.0` (default port `8080` for HTTP or `8443` for HTTPS), automatically discovers its local host and network LAN IP, and prints a console startup banner containing the exact active URLs (`http://...` or `https://...`).
- **The Web Interface uses relative pulls**: The web application is served directly by the server at `GET /`. Because it is served from the server origin, it does **not** need to configure or know the server address. All network calls are relative:
  - API Gateway: `POST /graphql`
  - Health / Status: `GET /status` (returns `{ "status": "ok", "protocol": "HTTP"|"HTTPS", "url": "..." }`)
  - Server Info: `GET /server-info`
  - GraphiQL Explorer: `GET /graphiql`
### Asymmetric Hybrid Channel Encryption for Insecure Networks (MITM Defense)
When operating over unencrypted HTTP (ports `80`, `8080`, or local LAN without TLS/SSL certificates), plain communication is vulnerable to Man-in-the-Middle (MITM) snooping and tampering. Quotes Connect implements a transparent, multiplatform **Asymmetric Hybrid Channel Encryption** protocol:
- **Automatic Port & Scheme Detection**: `ServerEndpointConfig.isSecureTransport` inspects the port (`443`, `8443`) and URL protocol (`https://`). When native TLS is active, standard transport security is utilized. When unencrypted HTTP is detected, the hybrid encrypted channel engages automatically.
- **Ephemeral RSA-2048 Server Keypair**: On launch, the Ktor server generates an ephemeral RSA-2048 keypair (`ServerChannelCrypto`) with key rotation capability.
- **Handshake Endpoint (`GET /api/channel/handshake`)**: Clients query this route to obtain the active `ChannelHandshakeResponse` (Base64 public key, `keyId`, and algorithm `RSA-2048/AES-GCM-256`).
- **Hybrid Request Envelope (`EncryptedChannelPayload`)**:
  - The client generates a single-use 256-bit AES symmetric key and a cryptographically secure 12-byte IV.
  - The request payload is encrypted using `AES/GCM/NoPadding`.
  - The symmetric AES key is encrypted using the server's ephemeral RSA public key (`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`).
  - Packaged and transmitted via `POST /api/channel/secure` or transparently to `POST /graphql` with `X-Encrypted-Channel: true`.
- **Server Decryption & Encrypted Response**: The server unwraps the AES key with its private key, decrypts the request, resolves the query or mutation, and encrypts the response body using the same symmetric AES key and a fresh 12-byte IV, returning `EncryptedChannelResponse`.
- **Transport Security UI Indicators**:
  - **`[SSL / TLS SECURED]`**: Rendered when connected over HTTPS / TLS.
  - **`[ENCRYPTED CHANNEL]`**: Rendered when connected over HTTP with active asymmetric hybrid envelope encryption.
  - **`[INSECURE HTTP]`**: Rendered if unencrypted HTTP is active without channel protection.
  - Available across Compose Multiplatform (`ChannelSecurityChip.kt` in desktop/mobile headers and `ServerEndpointDialog`), and Web UI (`#channelSecurityBadge`).

---

## 4. Passage Polymorphism & Graph Specifications

All information in Quotes Connect is an instance of `Passage`:

| Passage Type | Description | Special Rules |
| :--- | :--- | :--- |
| `PASSAGE` | Standard independent thought or note | May be voluntary advertised on Billboard by owner |
| `AUTHOR` | Sovereign identity moniker | Created via `createIdentity`; stores moniker in content |
| `QUOTE` | Preserved quotation | Contains author attribution, source reference, page number |
| `COMMENT` | Reaction or reflection | Must specify a target passage to subscribe to |
| `RECORDABLE` | Historical ledger axiom | Immutable; cannot be deleted; citations to it are locked |
| `SOURCE` | Citation to book, paper, article, or URL | Preserves bibliographic context |

### Directed Acyclic Graph (DAG) Invariant
- Every subscription forms a directed edge: `sourceId -> targetId` (`sourceId` subscribes to `targetId`).
- `DagValidator.wouldCreateCycle()` performs Breadth-First Search (BFS) before adding edges.
- Self-subscriptions (`sourceId == targetId`) and circular chains (`A -> B -> C -> A`) are strictly rejected.

### The Billboard Rules
- **Orphan Passages (`parentId == null`)**: Automatically and permanently advertised on the Billboard. They cannot be unadvertised by any user.
- **Owned Passages (`parentId != null`)**: The creator can voluntarily toggle advertisement on or off on the Billboard. Non-owners are denied modification permissions.

---

## 5. Standard Workflow & Execution Guide

### 1. Launch the Server

#### Standard HTTP Mode (Default)
```bash
./gradlew :server:run
```
*Console output will display:*
```
======================================================================
                 QUOTES CONNECT KNOWLEDGE SERVER
       Algorithm-Free Sovereign Knowledge Graph Platform
======================================================================
  • Protocol Mode:          HTTP
  • Local Web & API:        http://localhost:8080/
  • Network / LAN Address:   http://192.168.1.x:8080/
  • Interactive Explorer:   http://localhost:8080/graphiql
  • GraphQL Endpoint:       http://localhost:8080/graphql
  • Status Health Check:    http://localhost:8080/status
======================================================================
```

#### Secure HTTPS Mode (TLS / SSL)
```bash
# Switch to HTTPS mode using environment variables:
SERVER_PROTOCOL=https ./gradlew :server:run

# Or with custom port and keystore:
SERVER_PROTOCOL=https PORT=8443 SSL_KEYSTORE_PATH=/path/to/keystore.p12 ./gradlew :server:run
```
*Console output will display:*
```
======================================================================
                 QUOTES CONNECT KNOWLEDGE SERVER
       Algorithm-Free Sovereign Knowledge Graph Platform
======================================================================
  • Protocol Mode:          HTTPS (Secure TLS)
  • Local Web & API:        https://localhost:8443/
  • Network / LAN Address:   https://192.168.1.x:8443/
  • Interactive Explorer:   https://localhost:8443/graphiql
  • GraphQL Endpoint:       https://localhost:8443/graphql
  • Status Health Check:    https://localhost:8443/status
======================================================================
```
*(If no custom keystore is supplied, the server automatically generates and caches a self-signed developer certificate in `.quotes_connect/server_dev_keystore.jks`)*

### 2. Access the Web Application
Open any modern web browser to:
```
http://localhost:8080/
```
*(Or `http://<LAN_IP>:8080/` from any phone or computer on the same network).*

### 3. Launch the Desktop Client
In a separate terminal window:
```bash
./gradlew :app:desktopApp:run
```
The desktop app immediately connects to `http://localhost:8080`.

### 4. Run the Android Client
```bash
./gradlew :app:androidApp:assembleDebug
```
Install on emulator (connects via `10.0.2.2:8080`) or physical device (connects via `http://<LAN_IP>:8080`).

### 5. Run Verification Tests
```bash
./gradlew test
```

### 6. Build All Production Artifacts & Installers
```bash
./build-all-artifacts.sh
```
*Automates Android runtime installation, licenses, release keystore generation (with `.gitignore` protection), and generates:*
- **Server**: Executable Fat JAR (`dist/server/quotes-connect-server-1.0.0.jar`), Tarball, and Zip
- **Desktop**: Runnable Uber JAR (`dist/desktop/quotes-connect-desktop-1.0.0.jar`), Standalone Linux bundle (`dist/desktop/quotes-connect-desktop-1.0.0-linux-x64.tar.gz`)
- **Package Managers**: Debian package (`dist/desktop/quotes-connect_1.0.0_amd64.deb`) and Arch Linux pacman package (`dist/desktop/quotes-connect-bin-1.0.0-1-x86_64.pkg.tar.zst`)
- **Android**: Signed Release APK (`dist/android/quotes-connect-1.0.0-release.apk`) and Debug APK (`dist/android/quotes-connect-1.0.0-debug.apk`)
- **Checksums**: Comprehensive SHA-256 catalog (`dist/SHA256SUMS`)

### 7. Clean Project & Generated Artifacts
```bash
# Standard clean: executes gradle clean, deletes dist/ and all build directories
./clean.sh

# Deep clean: also removes local SQLite databases, dev keystores, and .gradle cache
./clean.sh --all

# Clean with database removal
./clean.sh --db

# Dry run: previews what would be removed without making changes
./clean.sh --dry-run
```

---

## 6. Project Checklist & Implementation Status

- [x] **KMP Multiplatform Setup**: Configured for Desktop, Android, Web (Wasm/JS), and Server.
- [x] **Core Domain & Models**: Polymorphic `Passage`, `Id`, `BillboardItem`, `Credentials`, `Tag`, `Notification`.
- [x] **DAG Cycle Detection**: Graph validation engine preventing circular dependencies.
- [x] **Graph Traversal & Shortest Path**: BFS pathfinder and descendant tree generation in `GraphEngine`.
- [x] **Zero Template Data Compliance**: Removed all pre-seeded dummy data. Clean database initialization.
- [x] **Ktor Server & Persistence**: Netty server with SQLite and MySQL JDBC persistence.
- [x] **GraphQL Gateway**: Custom lightweight AST parser supporting queries, mutations, variables, and introspection.
- [x] **Embedded Web Interface**: High-performance HTML/CSS/JS frontend served directly by Ktor using relative API pulls.
- [x] **Common Connection Contract**: `ServerEndpointConfig` & `ServerEndpointManager` implemented in `:core`.
- [x] **Shared Compose UI**: Billboard, Feed, Notifications, Passage Inspector, and dialogs in `:app:shared`.
- [x] **Desktop Application**: Working desktop runner in `:app:desktopApp`.
- [x] **Android Application**: Working native Android project in `:app:androidApp`.
- [x] **Linux Tree Citation Structure**: Citations and replies rendered below threads using authentic Linux `tree` branch formatting (`├── `, `└── `, `│   `). Implemented in both Compose Multiplatform UI and embedded Web UI.
- [x] **Copy Buttons for ID & Passage**: One-click clipboard copy buttons for passage ID and passage text on every card, inspector screen, and tree node across both Compose Multiplatform and Web interfaces.
- [x] **Author Billboard Default OFF & Self-Toggle**: Author profile passages represent sovereign identities that default to NOT advertised on the Billboard (`shouldAdvertise = false`, `isOrphan = false`). Authors own their identity node and can toggle their own billboard presence on and off at any time.
- [x] **Dedicated Profile Section**: Added a dedicated `PROFILE` tab in both Compose Multiplatform UI (`ProfileScreen.kt`) and the Web UI (`renderProfile()`). Displays author moniker, UUID (with copy button), passphrase (with copy button), billboard presence toggle button, and all passages created by this identity (including the author passage and all created thoughts).
- [x] **Remember Me Login/Logout Across All Clients**:
  - Added a "Remember me on this device" toggle in Compose Multiplatform (`IdentityDialog.kt`) and Web UI (`index.html`).
  - Persistent session storage (`PlatformSessionStore`) implemented across JVM (`~/.quotes_connect/session.json`), Android (`filesDir`), and Web (`localStorage`).
  - When Remember Me is enabled, credentials auto-restore across app launches.
  - When Remember Me is disabled, credentials remain in memory/session only.
  - Disconnecting/logging out wipes persistent credentials completely and switches to Anonymous mode.
- [x] **Process Start Directory SQLite Database**:
  - `ServerConfig.kt` and `ServerDatabase.kt` explicitly resolve relative SQLite file paths to the process starting directory (`System.getProperty("user.dir")`).
  - Ensures the database is always stored in the exact working directory where the server process was initiated.
- [x] **Strong Initial Alphanumeric Passphrase & Passphrase Changing**:
  - `Credentials.generatePassphrase(32)` produces high-entropy 32-character random alphanumeric strings (`[A-Za-z0-9]`), replacing word-based passphrases for strong brute-force resistance.
  - Added `changePassphrase` mutation in GraphQL Gateway, database verification in `JdbcPassageRepository` & `InMemoryKnowledgeRepository`.
  - Added Change Passphrase dialog and flow in Compose Multiplatform (`IdentityDialog.kt`, `ProfileScreen.kt`) and Web UI (`index.html`), allowing sovereign authors to update their passphrase at any time with automatic active session and "Remember Me" persistent credential sync.
- [x] **Universal Artifact Builder (`build-all-artifacts.sh`)**:
  - Automatic download and setup of Android Command-Line Tools & SDK components from Google web repository.
  - Automatic license agreements (`sdkmanager --licenses`).
  - Interactive/automatic 2048-bit RSA release signing keystore creation (`release.keystore` and `keystore.properties`) with automatic exclusion in `.gitignore`.
  - Builds Server executable Fat JAR (`server-all.jar`), Tarball, and Zip.
  - Builds Desktop runnable Uber JAR and standalone portable Linux bundle.
  - Builds Debian package (`quotes-connect_1.0.0_amd64.deb`) and native Arch Linux package (`quotes-connect-bin-1.0.0-1-x86_64.pkg.tar.zst`).
  - Builds signed Android Release APK and Debug APK.
  - Aggregates all outputs into `./dist` directory with full SHA-256 checksum verification manifest.
- [x] **Personal Chat & Private Citations with Asymmetric Encryption**:
  - **Two Distinct Chat Modes**: Explicitly differentiated between `ENCRYPTED` and `UNENCRYPTED` channels.
  - **Asymmetric End-to-End Encryption**: Utilizes 2048-bit RSA key pairs with AES-256-GCM hybrid envelope encryption (`ASYM:<key>:<iv>:<payload>`). Allows private citations and messages of any length.
  - **Zero Server Storage for Encrypted Chats**: Encrypted chat messages are strictly prohibited from disk/database persistence. Handled purely as ephemeral in-memory relay mailboxes (`ephemeralEncryptedMailbox`), guaranteeing zero server-side retention.
  - **Server Database Storage for Unencrypted Chats**: Plaintext chats are stored in the server SQLite/MySQL database table `unencrypted_chat_messages`.
  - **Client-Side Key Ownership**: Private keys are generated and stored exclusively on the user device (`LocalChatKeyStore`, `PlatformCrypto`). Private keys never leave the client and are never sent to the server.
  - **Mutual Handshake Protocol**: Either author can initiate a chat (`requestChat`); the recipient must explicitly accept (`respondChat`). Public keys are exchanged securely during handshake.
  - **Private Citations (`citedPassageId`)**: Messages can cite any passage in the graph as a private reference, clickable directly in the conversation thread.
  - **Multiplatform UI Support**: Complete chat interfaces built into Compose Multiplatform (`ChatScreen.kt` on Desktop and Android) and the embedded web interface (`index.html`).
- [x] **Dual Protocol Modes (HTTP & HTTPS) via Environment Variables**:
  - Added dynamic protocol mode switching between HTTP (port 8080 default) and HTTPS (port 8443 default).
  - Controlled by environment variables: `SERVER_PROTOCOL=http|https`, `PROTOCOL=http|https`, `SSL_ENABLED=true|false`, `SERVER_SSL_ENABLED=true|false`, or `HTTPS_ENABLED=true|false`.
  - Configurable keystore via `SSL_KEYSTORE_PATH`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_ALIAS`, `SSL_KEY_PASSWORD`.
  - Zero-friction automated fallback: generates and caches a valid self-signed developer certificate via Ktor TLS certificates (`.quotes_connect/server_dev_keystore.jks`) when in HTTPS mode with no external keystore.
  - Endpoints (`/status`, `/server-info`) and startup banner dynamically broadcast the active protocol and URL schemes.
- [x] **Universal Workspace & Artifact Cleaner (`clean.sh`)**:
  - Automatically handles Java discovery (`JAVA_HOME`) across SDKMAN and system environments.
  - Executes Gradle clean with `--no-configuration-cache --no-daemon` to prevent cache report locking I/O conflicts.
  - Completely deletes distribution directory (`dist/`) containing server JARs, desktop bundles, Debian packages, Arch packages, Android APKs, and checksums.
  - Cleans all module `build/` directories, compiler caches (`.kotlin`, `kotlin-js-store`, `.cxx`), and temporary packaging stages.
  - Supports flags: `--all` (deep clean including local databases, dev keystores, and `.gradle`), `--db` (SQLite databases), `--cache` (`.gradle`), and `--dry-run`.
- [x] **Unified Sovereign App Icon Across Web, Desktop, Android, and Web**:
  - **Design Motif**: Selected official icon featuring an elegant pair of white quotation marks seamlessly fused with a graph network of interconnected nodes and thin connecting lines on a pitch-black squircle canvas (`quotes_connect_logo_1791131063750.jpg`). Perfectly embodies "Quotes Connect" — where individual quotations and citations form a living, sovereign knowledge graph.
  - **Web & Browser**: Generated `favicon.svg` (embedding high-res raster data), multi-resolution `favicon.ico`, `icon.png` (512x512), `icon-192.png`, `apple-touch-icon.png` (180x180), and `site.webmanifest` served dynamically by Ktor and linked in `index.html`.
  - **Desktop Application**: 512x512 high-res `icon.png` and multi-size `icon.ico` (16 to 256px) in `app/desktopApp/src/main/resources`, window icon wired via Compose `painterResource("icon.png")`, native Linux/macOS/Windows distribution packages configured in `app/desktopApp/build.gradle.kts`, and automatic pixmaps packaging in `build-all-artifacts.sh` for Debian (`.deb`) and Arch (`.pkg.tar.zst`).
  - **Android Application**: Adaptive icon with pure black background (`ic_launcher_background.xml`), smooth antialiased centered graphic foreground (`ic_launcher_foreground.png` scaled across all densities: `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`), and full squircle mipmaps (`ic_launcher.png`) + circular-masked mipmaps (`ic_launcher_round.png`).
- [x] **Server Route Modularization & Clean Separation**:
  - Extracted route handlers from the monolithic `Application.kt` into dedicated, idiomatic Ktor route extensions under `connects.quotes.routes`:
    - `WebRoutes.kt`: Serves static web assets, `/status`, `/server-info`, and `/graphiql` explorer; includes `getLanIpAddress()`.
    - `ChatRoutes.kt`: Handles persistent live TCP chat SSE streaming (`/api/chat/live`), notification streams (`/api/chat/notifications`), typing broadcasts (`/api/chat/typing`), and resync requests (`/api/chat/resync`).
    - `AuthorRoutes.kt`: Manages heartbeat pings (`/api/author/ping`), author settings (`/api/author/settings`), inactivity sweeps (`/api/author/inactivity-check`), and voluntary discards (`/api/author/discard`).
  - Reduced `Application.kt` to a clean 145-line entrypoint managing server startup, TLS/SSL configuration, and plugin installations.
- [x] **Real-time Live Chat TCP Streaming Connection & Notifications**:
  - Live TCP streaming connection (`GET /api/chat/live`) keeps persistent streaming active while a chat is open and instantly disconnects when the user exits.
  - In-app notification badge & listener (`GET /api/chat/notifications`) alerts users to incoming messages when the specific chat is not currently open.
  - Asymmetric Key Changed Stack & Re-Exchange: Detects client key losses (e.g., app reinstall), prompts key re-exchange (`renewChatKey`), and updates peer sessions.
  - Client-Side Persistent Database: Local client storage (`LocalChatDatabase`) maintains a persistent record of all encrypted and unencrypted messages across client restarts.
- [x] **Android Layout Optimizations & Modular Progressive Web UI**:
  - Android UI enhanced with edge-to-edge support, compact touch targets, adaptive split-pane chat views, and scroll performance improvements.
  - Web UI rebuilt with modular CSS/JS structure, skeleton loaders, and progressive component transitions.
- [x] **Cloud-Backed Encrypted Chat (`ENCRYPTED_CLOUD`) & Keystore Management**:
  - **Third Chat Mode (`ENCRYPTED_CLOUD`)**: Asymmetric E2EE messages persisted as ciphertext in server database table `encrypted_cloud_chat_messages`. Zero plaintext stored on server.
  - **Symmetric Keystore Cloud Backup**: Client private keys encrypted client-side with user password via PBKDF2 + AES-GCM and stored in `author_keystores`.
  - **Locked State & Gating**: When a client has not decrypted the keystore, encrypted messages are shown as locked references/placeholders, and sending new messages is disabled.
  - **Chat Creation Guard**: When starting an encrypted chat (`ENCRYPTED` or `ENCRYPTED_CLOUD`), prompts user to create a keystore with password in settings or decrypt existing cloud backup.
  - **Keystore Reset**: Users can wipe cloud and local keystore, generate a fresh keypair encrypted with a new password, back it up to cloud, and push the renewed public key (`renewChatKey`) to active chats.
  - **Peer Resynchronization Request (`RESYNC_REQUEST`)**: Users can manually request peer to reshare historical messages (`requestChatResync`).
  - **Typing Animation ("quoting...")**: While both authors have the same chat open, typing in the input box streams a live `TYPING` packet, rendering a pulsing indicator stating **"quoting..."**.
- [x] **Author Activity Logging, Inactivity Sweep, Sovereign Discard Actions, and Author Page**:
  - **Activity Logging & Heartbeat**: Every time an author connects to live chat, streams notifications, pings (`POST /api/author/ping`), updates settings, or creates content, activity is logged in `author_activity_logs` and `last_active_at` is updated in `author_settings`.
  - **Configurable Inactivity Period & Tag**: Authors specify their inactivity threshold (e.g., 1d, 7d, 14d, 30d, 60d, 90d, 180d, 365d). The author's sovereign identity passage is automatically tagged with `inactivity:${days}d`.
  - **Server Inactivity Check (`performInactivityCheck`)**: Background periodic worker (runs every 15 minutes) and on-demand endpoints (`POST /api/author/inactivity-check`, GraphQL `mutation { performInactivityCheck }`). If elapsed time exceeds author's threshold:
    1. Permanently deletes all chat messages sent from the author's side (both unencrypted and encrypted cloud).
    2. Processes unadvertised passages based on configured `discardAction`:
       - `ORPHAN`: Strips ownership (`parentId = null`) and promotes thoughts to the Billboard as public domain orphan knowledge (`isOrphan = true`, `isAdvertised = true`).
       - `DELETE`: Permanently deletes unadvertised passages (ledger-immutable `RECORDABLE` passages are protected and always converted to orphans).
  - **Voluntary Author Discard (`discardAuthor` / `POST /api/author/discard`)**: When an author manually chooses to delete their identity, the exact same discard action is executed, deleting chats, orphaning or deleting unadvertised citations, and resetting sessions.
- [x] **Zero-Hardcoding Multiplatform Build Script (`build-all-artifacts.sh`)**:
  - **Dynamic JDK Discovery (No Hardcoding)**: Scans `$JAVA_HOME`, `PATH` (`command -v java` with symlink resolution), dynamic `$HOME/.sdkman/candidates/java/current`, macOS `/usr/libexec/java_home`, system JDKs (`/usr/lib/jvm/*`), and `$HOME/.jdks/*` without hardcoding any user or machine paths.
  - **Clear Warning on Missing JDK**: If JDK 17+ is absent, emits an informative warning with OS-specific package manager commands (Arch Linux, Debian/Ubuntu, Fedora, Alpine, macOS Homebrew, SDKMAN!) and export instructions, exiting cleanly with status 1.
  - **Android SDK Auto-Install Only When Absent**: Probes `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`, `local.properties` (`sdk.dir`), `$HOME/Android/Sdk`, macOS, and system paths. If an existing SDK is detected, skips all downloads and reuses it; only if completely absent does it auto-download and install Android command-line tools from Google CDN.
  - **Automatic SDK License Acceptance**: Automatically accepts all Android SDK licenses via `yes | sdkmanager --licenses` prior to compilation.
  - **Clean gradle.properties & clean.sh**: Eliminated user-specific hardcoded paths from `gradle.properties` and `clean.sh`, ensuring full cross-machine portability.
  - **Comprehensive Multiplatform Packaging**: Produces all 9 platform targets into `./dist` (Server Fat JAR, Tar, Zip; Desktop Uber JAR, Linux standalone tarball; Debian `.deb`; Arch Linux `.pkg.tar.zst`; Android Release & Debug APKs) complete with consolidated `SHA256SUMS`.
- [x] **Simplified 3-Button Chat Channel Toggle & Keystore Gating**:
  - Replaced channel dropdown with a clean, toggle-like 3-button selector: **`Unencrypted Cloud`** (`UNENCRYPTED`), **`Encrypted On-Device`** (`ENCRYPTED`), and **`Encrypted Cloud`** (`ENCRYPTED_CLOUD`).
  - Gating: If the user lacks an initialized/unlocked keystore, the third option (`Encrypted Cloud`) is hidden. Unlocking or creating the keystore immediately reveals it.
  - Gated prompt for creating or accepting encrypted chats:
    *"You don't have a keystore to continue this conversation. Please create a keystore with key phrase to continue or start this conversation."*
- [x] **Encrypted Cloud Handshake & Keystore Relay Buffer**:
  - Secure asymmetric public key exchange for encrypted cloud chats.
  - Server relay buffer (`pending_chat_keys` table & `/api/chat/pending-keys`): Holds incoming public keys if the peer is inactive. When the peer unlocks their keystore, keys are pushed, appended to local store, acknowledged via `/api/chat/pending-keys/ack`, and wiped from the server buffer.
  - Chat resync is only required when a keystore is regenerated/reset.
- [x] **Chat Aliases (Personal vs. Shared)**:
  - **Personal Alias**: Stored strictly on the local client device (e.g., local storage or client SQLite database) without any server exposure.
  - **Shared Alias**: Proposed by one participant, accepted by the counterparty via `/api/chat/alias/propose` and `/api/chat/alias/accept` (or GraphQL `proposeChatAlias` / `acceptChatAlias`). Displayed with peer confirmation banner and `[ACCEPT]` / `[DISMISS]` actions.
- [x] **Author Profile & Unique Username / Alias for Easy Login**:
  - Added unique `@username` and author bio in `author_settings`.
  - Seamless login resolution via `/api/author/resolve` supporting `@username` + passphrase or UUID + passphrase.
- [x] **Direct Subscriptions & Dedicated Subscribed Feed**:
  - Allows users to bookmark/follow authors or passages directly without citing them (`author_subscriptions` table, GraphQL `subscribeAuthorPassage` / `unsubscribeAuthorPassage` / `subscribed`).
  - Dedicated **`SUBSCRIBED`** tab with card list and search filtering in both Web UI and Compose Multiplatform.
  - "Start New Chat" modal includes suggested subscribed authors chips/dropdown to start conversations directly without hunting UUIDs on the Billboard.
- [x] **Universal Search & Filtering Across All Tabs**:
  - Responsive search input fields and type filters implemented across Billboard, Feed, Subscribed, and Chat screens.
- [x] **Persistent SSE Event Connection & Adaptive UI**:
  - Persistent SSE stream `/api/events` (`GRAPH_CHANGED`, `ALIAS_PROPOSAL`, `ALIAS_ACCEPTED`) delivering real-time, non-destructive UI updates without manual page refreshes.
- [x] **Safe Web Refresh via URL Hash Routing**:
  - Browser URL hash routing (`#billboard`, `#feed`, `#subscribed`, `#notifications`, `#chat?id=...`, `#author`, `#detail?id=...`) prevents browser refreshes from resetting back to the Billboard.
- [x] **Per-Passage and Bulk Type-Based Billboard Visibility Controls**:
  - **Granular Passage Visibility**: Authors can toggle the Billboard advertisement of each individual owned passage directly from the Author screen (`[ADVERTISED]` vs `[PRIVATE / UNADVERTISED]`).
  - **Bulk Type-Based Management**: Authors can bulk advertise or unadvertise thoughts by passage type (`QUOTE`, `PASSAGE`, `COMMENT`, `RECORDABLE`, `SOURCE`, or `ALL`).
  - **Strict Orphan Immutability**: All bulk and individual visibility operations enforce `is_orphan = 0`. Public domain orphan passages remain permanently advertised and cannot be removed by any user action.
  - **Multiplatform UI & API**: Wired across Compose Multiplatform (`AuthorScreen.kt`), Web UI (`index.html`), GraphQL mutations (`setPassageTypeAdvertisement`, `advertisedPassageIds`), and REST routes (`/api/author/passage-visibility`, `/api/author/advertised-passages`).
- [x] **Lazy Feed Evaluation with Recursive Child Discovery**:
  - **Lazy On-Demand Traversal**: The Feed contains child passages of all subscribed entities evaluated lazily upon query pull (`query { feed(authorId, depth, limit, offset) }`):
    - Subscribed Authors: lazily reveals all owned creations by the author (`parent_id IN (...)`).
    - Subscribed Quotes / Passages: lazily reveals all citing attachments / comments referencing the node (`subscriptions WHERE target_id IN (...)`).
  - **Progressive Depth Discovery**: Interactive depth controls (`depth = 1` for direct children, `depth = 2` for deeper connections, `depth = 3` for extended network traversal) allow progressive graph discovery without ahead-of-time processing overhead.
  - **Strict Notification Scoping**: Zero notifications are generated for feed discoveries; notifications are strictly reserved for chat messages and direct citations to the author's owned thoughts (`target_passage_id = ? OR target_passage_id IN (SELECT id FROM passages WHERE parent_id = ?)`).
- [x] **Server Tag Database, 500ms Debounced Autocomplete, Comma-to-Chip LazyRow, & Multiplatform Caching**:
  - **Server Database Tag Index with Usage Counts**: `global_tags` table maintains all tags with their actual usage frequency (`count`), updated automatically whenever any passage arrives.
  - **Default Aesthetic Seed Tags on Clean Run**: When the database is initialized with zero tags on a clean server run, seeds 16 aesthetic project tags: `stoicism`, `philosophy`, `wisdom`, `sovereign`, `knowledge`, `dag`, `citation`, `epistemology`, `aphorism`, `truth`, `ethics`, `logic`, `reason`, `virtue`, `mind`, `literature`.
  - **500ms "Thinking" Debounce Autocomplete**: When typing in the tag input field, waits for a 500ms pause before fetching/filtering top matching tags by usage count (prefix matches prioritized, followed by substring matches).
  - **Comma-to-Chip LazyRow UX**: Typing a comma `,` or pressing Enter instantly extracts the tag, clears the input field, and places the tag as a chip button in a horizontal scrollable `LazyRow` with a quick-remove `×` button, completely eliminating clumped text strings.
  - **Zero-Latency Client Tag Cache (Desktop & Android)**: `ClientTagCache` maintains an in-memory index backed by persistent storage (`~/.quotes_connect/tags_cache.json` on JVM, `quotes_connect_tags_cache.json` on Android, `localStorage` on Web), reducing network calls, preventing server wastage, and enabling instant offline autocomplete.
- [x] **Monochrome Editorial & Brutalist Utility UI/UX Design System (v1.0)**:
  - Fully integrated the 10 Core Design Principles and The Prime Directive across Compose Multiplatform (Android, Desktop) and Web UI.
  - Standardized monochrome tokens (`PureBlack` #000000, `Surface01` #111111, `Surface02` #181818, `PureWhite` #FFFFFF, `Gray01` #B5B5B5, `Gray02` #666666, `Gray03` #333333) with restrained subdued semantics.
  - Implemented architectural corner radius hierarchy: `0px` structural surfaces, `4px` inputs/chips, `6px` cards/modals, `8px` buttons.
  - Enforced 1px thin low-contrast borders and generous negative space.
  - Unified microcopy voice across all screens ("Create Sovereign Identity", "SOVEREIGN COMMONS", uppercase utility typography, dignified empty states).
  - Maintained 100% backward compatibility and zero disruption to existing features.
- [x] **Asymmetric Hybrid Channel Encryption & Transport Security Chips (Plain HTTP MITM Protection)**:
  - **Port & Transport Security Detection**: Dynamically inspects listening ports (`443`, `8443`) and protocol schemes (`https://`) in `ServerEndpointConfig.isSecureTransport`. Automatically engages asymmetric envelope encryption when communicating over plain HTTP (e.g. port `8080`, `80`, or local LAN without TLS) while preserving direct requests when native TLS is active.
  - **Ephemeral Server Key Exchange**: Server generates an in-memory ephemeral RSA-2048 keypair (`ServerChannelCrypto`) with key rotation capability. Serves public key via `GET /api/channel/handshake` (`ChannelHandshakeResponse`).
  - **Hybrid Request Envelope (`EncryptedChannelPayload`)**: Clients generate a fresh 256-bit AES key and 12-byte IV for each request, encrypt the GraphQL query/mutation with `AES/GCM/NoPadding`, wrap the AES key using the server's ephemeral RSA public key (`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`), and dispatch to `POST /api/channel/secure` or transparently to `POST /graphql` with `X-Encrypted-Channel: true`.
  - **Encrypted Server Response**: Server unwraps the AES key, decrypts the request, resolves the query or mutation, and encrypts the response body with the same AES key and a fresh 12-byte IV (`EncryptedChannelResponse`).
  - **Cross-Platform Cryptography (`PlatformCrypto.kt`)**: Implemented across JVM, Android, JS, and WasmJS with zero external runtime dependencies.
  - **Brutalist Transport Security UI Chips**:
    - `[SSL / TLS SECURED]`: Displayed when connected over HTTPS/TLS.
    - `[ENCRYPTED CHANNEL]`: Displayed when connected over HTTP with active asymmetric hybrid envelope encryption.
    - `[INSECURE HTTP]`: Displayed as a warning badge if connected over unencrypted HTTP without channel protection.
    - Rendered in Desktop header, Mobile header, `ServerEndpointDialog`, and Web Single Page App (`#channelSecurityBadge`).
- [x] **Comprehensive Security Hardening & Architectural Recovery Plan**:
  - **Multiplatform CSPRNG Engine (`SecureRandomSource`)**: Replaced all pseudo-random generators with OS-native CSPRNG across all supported compilation targets (JVM `java.security.SecureRandom`, Android `SecureRandom`, JS `crypto.getRandomValues`, and WasmJS `WebCrypto`), providing high-entropy generation for `Id`, passphrases, IVs, salts, and session tokens.
  - **Multiplatform Monotonic/Epoch Time (`PlatformTime.kt`)**: Implemented clean `currentTimeMillis()` across JVM, Android, JS, and WasmJS, eliminating JVM-specific time leaks in common modules.
  - **Standardized Cryptographic Protocol (`QC-RSA2048-AES256GCM-OAEP`)**: Upgraded client and server crypto to RSA/ECB/OAEPWithSHA-256AndMGF1Padding and AES-256-GCM (12-byte IV, 128-bit tag, AAD channel context binding). Keystore protection upgraded to PBKDF2-HMAC-SHA256 (100,000 iterations, 16-byte salt). All unimplemented targets fail closed with `UnsupportedSecureOperation`.
  - **PBKDF2 Salted Credential Verifiers (`auth_credentials`)**: Eliminated raw passphrase database storage in favor of salted PBKDF2-HMAC-SHA256 verifiers with 100,000 iterations, 16-byte salts, and constant-time verification. Automatic lazy credential migration upon login.
  - **Revocable Bearer Session Tokens (`auth_sessions`)**: Server-issued 256-bit CSPRNG bearer tokens stored hashed (`token_hash` SHA-256). Handled via `/api/auth/login`, `/logout`, `/session`, and `/refresh` with token rotation and multi-device revocation.
  - **Zero-Dependency AST Root-Field GraphQL Gateway (`GraphQLDocumentParser.kt`)**: Replaced fragile substring matching with a robust AST root-field parser supporting aliases, arguments, directives, and balanced selection-set extraction. Eliminates false-positive dispatching and nested mutation injection.
  - **Server-Side Authorization Matrix (`AuthPrincipal`)**: Enforces caller identity checks on all GraphQL mutations and REST routes (chat, settings, visibility, heartbeat, subscriptions, discards), rejecting unauthorized operations with 403 Forbidden.
  - **Client Bearer Session Persistence (`PlatformSessionStore.kt`)**: Multiplatform session storage across JVM, Android, JS, and WasmJS preserving revocable tokens with zero plaintext passphrase storage on client disks.
  - **Database Schema Migrations & Structured Audit Logging**: Transactional `schema_migrations` tracking, and tamper-resistant structured audit logging (`security_audit_logs`) without credential or payload leakage.
  - **Adversarial Security Test Suite (`AuthSecurityTest.kt`)**: Comprehensive test suite verifying PBKDF2 verification, AST parser isolation against substring exploits, bearer session lifecycle, token refresh/rotation, and identity spoofing rejection.
- [x] **Cryptographic Identity & Sovereign Signing Hierarchy**:
  - Defined authoritative multi-tier cryptographic identity hierarchy (`CRYPTO_ADR.md`): Sovereign Author ID, Signing Identity, RSA-OAEP 2048 Encryption Identity, and Ephemeral AES-256-GCM Session Keys.
  - 100% elimination of placeholder crypto (`PUB-WEB-*`, `PRIV-WEB-*`) in favor of native WebCrypto (`window.crypto.subtle`) in the browser.
  - Multiplatform `LocalChatKeyStore` with PBKDF2 (100k rounds) + AES-256-GCM encrypted-at-rest protection and POSIX `0600` file permissions.
  - Fail-closed security architecture rejecting unauthorized callers and preventing any silent plaintext downgrade.
- [x] **Interactive Visual Graph Canvas**:
  - Implemented interactive 2D node-and-edge visual graph rendering across Compose Multiplatform (`VisualGraphCanvas.kt`) and HTML5 Canvas (`#inspectorGraphCanvas`).
  - Features force/radial topological layout (root node, parent author, cited nodes, comments), pan, pinch/wheel zoom, reset controls, directional edge arrows, and click-to-inspect hit-testing.
  - Seamless dual-mode inspector switching between Linux ASCII branch trees (`[TREE]`) and the interactive visual graph canvas (`[CANVAS]`).

---

## 7. Persistent Memory Log for Antigravity AI

*Whenever interacting with this codebase:*
1. Always maintain the strict separation between `:server`, `:app:desktopApp`, `:app:androidApp`, and `:core`.
2. Do **not** reintroduce any hardcoded template, sample, or seed data. All passages must originate from user actions.
3. Keep the embedded web UI relying exclusively on relative URLs (`/graphql`, `/status`, `/api/*`).
4. Keep Desktop and Android using the shared `ServerEndpointConfig` in `:core`.
5. Chat channel selection must be toggle buttons: `Unencrypted Cloud`, `Encrypted On-Device`, and `Encrypted Cloud`.
6. Keystore prompt must state: *"You don't have a keystore to continue this conversation. Please create a keystore with key phrase to continue or start this conversation."*
7. Ensure hash routing handles browser refreshes without resetting views.
8. Update this file whenever new architectural decisions, modules, or features are introduced.
9. Transport security must automatically detect port/scheme: when plain HTTP is detected, ephemeral asymmetric key exchange and AES-256-GCM envelope encryption must protect all communication against MITM attacks, displaying appropriate UI badges (`[SSL / TLS SECURED]` vs `[ENCRYPTED CHANNEL]`).
10. Always consult `completed.md` for the authoritative register of implemented features, protocols, and security subsystems, and keep both `plan.md` and `completed.md` synchronized whenever new functionality is delivered.

