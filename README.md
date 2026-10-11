# Quotes Connect (QC)

> **Algorithm-Free Sovereign Knowledge Graph Platform**  
> Built with Kotlin Multiplatform (KMP), Compose Multiplatform, and Ktor.

---

## 1. About the Project

**Quotes Connect** is an algorithm-free, sovereign knowledge network designed to connect human thoughts, quotes, comments, sources, and axioms without algorithmic manipulation, engagement bait, or vanity metrics.

Traditional social media platforms use opaque recommendation algorithms that optimize for addiction and conflict. In Quotes Connect:
- There is **no algorithmic feed**.
- Thoughts and quotations are organized as **Passages** forming a **Directed Acyclic Graph (DAG)**.
- Discovery happens in two pure ways:
  1. **The Billboard**: A public knowledge commons displaying self-advertised passages and mandatory public orphan thoughts.
  2. **The Feed**: A personalized graph projection constructed exclusively from explicit, user-directed subscriptions.
- Relationships between passages are explicit citations and subscriptions. You explore ideas by traversing trees and discovering shortest paths between thoughts.

---

## 2. Core Philosophy & System Behavior

### Zero Template / Pre-Seeded Data
Quotes Connect does **not** generate or bundle any rough, mock, or template data. The database and knowledge graph start pristine and empty. Every quote, author identity, axiom, and connection in the network is authored directly by the user.

### Associative Anonymity & Privacy Guarantee
While the server internally tracks the creator lineage (`parentId`) to authorize deletions and deliver citation notifications, this creator ID is **strictly omitted from public serializations and GraphQL payloads**. Thoughts stand on their own merit and connections rather than personal notoriety.

### Directed Acyclic Graph (DAG) Invariants
Passages can subscribe to other passages to form reaction chains, comment threads, or citation networks. The system enforces DAG constraints via `DagValidator`:
- Self-subscriptions are rejected.
- Transitive loops ($A \rightarrow B \rightarrow C \rightarrow A$) are detected via BFS and strictly prohibited.

### The Billboard Rules
- **Orphan Passages (`parentId == null`)**: If a passage is created without an owner, it is deemed public commons knowledge and is **mandatorily and permanently advertised on the Billboard**. It cannot be unadvertised.
- **Owned Passages (`parentId != null`)**: The creator can voluntarily choose to advertise or unadvertise their passage on the Billboard.

### Ledger Immutability
- Passages designated as `RECORDABLE` represent immutable axioms or permanent citations.
- Once created, a `RecordablePassage` cannot be deleted.
- Any passage cited by a recordable passage is also locked against deletion to preserve historical ledger integrity.

---

## 3. Architecture & Module Separation

Every module in Quotes Connect is strictly isolated and purpose-built:

```
Quotes Connect
├── core/                  # Shared Domain & Graph Logic (Pure Kotlin Multiplatform)
│   ├── model/             # Polymorphic Passage, Id, BillboardItem, Credentials
│   ├── graph/             # DagValidator, GraphEngine (Shortest path BFS & trees)
│   ├── usecase/           # Create, Delete, Subscribe, Traverse, ToggleAdvertisement
│   └── config/            # ServerEndpointConfig & Manager (Shared Desktop/Android contract)
│
├── server/                # Independent Ktor Netty Backend & Web Host
│   ├── Application.kt     # Server entrypoint, address discovery, GraphQL routing
│   ├── database/          # SQLite (Demo) and MySQL (Production) JDBC repositories
│   ├── graphql/           # GraphQL AST parser, resolvers, and GraphiQL IDE
│   └── resources/web/     # Single-page web UI served directly at http://<server>/
│
└── app/
    ├── shared/            # Compose Multiplatform UI, ViewModel, network clients
    ├── desktopApp/        # Desktop application for macOS, Linux, Windows
    ├── androidApp/        # Native Android app
    ├── webApp/            # Kotlin/Wasm and Kotlin/JS browser app
```

---

## 4. How the Pieces Work Together

### 1. The Server Starts First
The server runs as an independent process. It embeds and automatically knows its bound address. Upon booting, it inspects network interfaces and outputs its addresses directly to the terminal:
- **Local Web & API**: `http://localhost:8080/`
- **Network / LAN Address**: `http://<LAN_IP>:8080/` (e.g. `http://192.168.1.50:8080/`)
- **GraphQL Endpoint**: `http://localhost:8080/graphql`
- **Interactive GraphiQL Explorer**: `http://localhost:8080/graphiql`

### 2. The Website Needs Zero Configuration
The web application is served directly by the server from `GET /`. Because it is served directly from the host origin, the website **does not need to know or configure the server address**. It uses **relative pulls** (`/graphql`, `/status`) out of the box.

### 3. Desktop and Android Connect via Shared Contract
Both the Desktop client and Android client communicate with the server through the common configuration class:
`connects.quotes.config.ServerEndpointConfig` (located in `:core`).
- **Desktop Client**: Connects to `http://localhost:8080` by default.
- **Android App**: Connects to `http://10.0.2.2:8080` (emulator host) or `http://<LAN_IP>:8080` (physical device on Wi-Fi).
- Endpoints can be updated dynamically at runtime via the in-app connection dialog.

---

## 5. Getting Started & Running the Project

### Prerequisites
- JDK 17 or JDK 21 configured in your environment (`JAVA_HOME`).
- Android SDK (if building the Android app).

### Step 1: Start the Server

#### Standard HTTP Mode (Default: Port 8080)
```bash
./gradlew :server:run
```
The server will start, verify the database schema, and display the connection URLs in your console.

#### Secure HTTPS Mode (TLS/SSL: Default Port 8443)
Start in HTTPS mode simply by setting `SERVER_PROTOCOL=https`:
```bash
SERVER_PROTOCOL=https ./gradlew :server:run
```
Or configure custom ports and certificates:
```bash
SERVER_PROTOCOL=https PORT=8443 SSL_KEYSTORE_PATH=/path/to/keystore.jks SSL_KEYSTORE_PASSWORD=secret ./gradlew :server:run
```
> **Automatic Dev Certificate**: If no keystore is supplied, the server automatically generates and caches a self-signed developer certificate in `.quotes_connect/server_dev_keystore.jks`.

### Step 2: Access the Web App
Open any browser to:
```
http://localhost:8080/      # In HTTP mode
https://localhost:8443/     # In HTTPS mode
```
Explore the Billboard, switch to Feed, generate an Author identity, or post your first passage. (All web API requests use relative paths, automatically adapting to HTTP or HTTPS).

### Step 3: Run the Desktop Application
In a separate terminal:
```bash
./gradlew :app:desktopApp:run
```
Or with hot reload:
```bash
./gradlew :app:desktopApp:hotRun --auto
```

### Step 4: Run the Android App
```bash
# Build debug APK
./gradlew :app:androidApp:assembleDebug

# Or install directly to connected device / emulator
./gradlew :app:androidApp:installDebug
```

### Step 5: Run Automated Tests
```bash
# Run all multiplatform tests
./gradlew test

# Run core domain and graph rules tests
./gradlew :core:jvmTest

# Run Ktor server and GraphQL tests
./gradlew :server:test

# Run shared client logic tests
./gradlew :app:shared:jvmTest
```

### Step 6: Build All Production Artifacts & Packages
```bash
./build-all-artifacts.sh
```
Builds the server Fat JAR/Tar/Zip, runnable desktop JAR/Linux bundle, Debian (`.deb`) and Arch (`.pkg.tar.zst`) packages, and signed Android APKs into `./dist`.

### Step 7: Clean Project & Generated Artifacts
```bash
# Standard clean: runs gradle clean, removes dist/ and all build/ directories
./clean.sh

# Deep clean: also removes local SQLite databases, dev keystores, and .gradle cache
./clean.sh --all

# Clean with database removal
./clean.sh --db

# Dry run: preview what would be cleaned without removing anything
./clean.sh --dry-run
```

---

## 6. GraphQL API Reference

The server exposes a lightweight GraphQL API at `/graphql`. You can also explore and test queries interactively at `http://localhost:8080/graphiql`.

### Common Queries

#### Query Billboard Items
```graphql
query {
  billboard {
    isOrphan
    passage {
      id
      type
      content
      tags
      isRecordable
      subscriberIds
      subscribedToIds
    }
  }
}
```

#### Query Single Passage
```graphql
query {
  passage(id: "UUID-HERE") {
    id
    type
    content
    tags
    subscriberIds
    subscribedToIds
  }
}
```

### Common Mutations

#### Create Passage
```graphql
mutation {
  createPassage(
    type: "QUOTE"
    content: "The unexamined life is not worth living."
    tags: ["philosophy", "socrates"]
  ) {
    id
    type
    content
    tags
  }
}
```

#### Generate Sovereign Author Identity & Session
```graphql
mutation {
  createIdentity(moniker: "Hypatia") {
    passageId
    passphrase
    token
  }
}
```

---

## 7. Cryptographic Architecture & Sovereign Invariants

Quotes Connect implements a strict fail-closed cryptographic architecture:
- **No Mock / Placeholder Crypto**: 100% real Web Crypto API (`window.crypto.subtle`) in the browser and JCE on JVM/Android.
- **Asymmetric Envelopes**: RSA-OAEP 2048-bit with SHA-256 for key agreement and plain HTTP hybrid envelope encryption.
- **Symmetric Ciphers**: AES-256-GCM with 12-byte CSPRNG IVs, 128-bit authentication tags, and conversation-bound Authenticated Additional Data (AAD).
- **Keystores at Rest**: Current keystore envelopes use PBKDF2-HMAC-SHA256 with 600,000 iterations, a 16-byte salt, and AES-256-GCM. Legacy keystore envelopes remain readable at 100,000 iterations. Server credential verifiers are a separate protocol using 100,000 iterations.
- **Fail-Closed Authorization Matrix**: Server-side validation (`principal != null && principal.authorId == resourceOwner`) across all private GraphQL mutations and REST endpoints.
- Detailed specifications are documented in [CRYPTO_ADR.md](CRYPTO_ADR.md) and [THREAT_MODEL.md](THREAT_MODEL.md).

---

## 8. Interactive Visual Graph Canvas

Quotes Connect provides dual-mode topological inspection for any passage node:
- **`[TREE]` Mode**: Clean, hierarchical Linux ASCII branch tree formatting (`├── `, `└── `).
- **`[CANVAS]` Mode**: Interactive 2D hardware-accelerated visual graph canvas rendering root nodes, citing thoughts, parent authors, and comments with directional arrows, drag panning, pinch/wheel zoom, hit-testing, and hover tooltips.
- Available across Compose Multiplatform (Desktop, Android) via `VisualGraphCanvas.kt` and Web via `<canvas id="inspectorGraphCanvas">`.

---

## 9. Verification & Architectural Audit

Quotes Connect is verified by an automated multiplatform test suite:
- The repository contains server/shared test suites covering GraphQL resolvers, authorization, PBKDF2 authentication, live event handling, DAG validation, and cryptographic envelopes.
- Historical audit reports may contain earlier compile/test totals; they are not current release evidence.
- Current environment verification is **source/static verified but not compile-verified** because Gradle 9.5.1 cannot be downloaded in this environment. Runtime/browser verification remains a release gate.
- Current status and acceptance gates are recorded in [CURRENT_STATUS.md](CURRENT_STATUS.md); historical audit material is retained separately.

---

## 10. Persistent Project Plan & System Register

For the ongoing architectural roadmap and authoritative register of all implemented subsystems:
- [plan.md](plan.md) — Core manifesto, design system, and roadmap.
- [completed.md](completed.md) — Definitive technical inventory of all implemented features.
- [CRYPTO_ADR.md](CRYPTO_ADR.md) — Cryptographic Architecture Decision Record.
- [THREAT_MODEL.md](THREAT_MODEL.md) — System Threat Model & STRIDE analysis.
- [FINAL_AUDIT.md](FINAL_AUDIT.md) — Final Remediation Audit Checklist.
- [UI_REVIEW.md](UI_REVIEW.md) — UI/UX Design System & Ergonomics Review.