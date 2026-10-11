#!/usr/bin/env bash
# ==============================================================================
# Quotes Connect — Universal Multiplatform Artifact & Package Builder
# ==============================================================================
# Generates all platform artifacts:
#   1. Server: Executable Fat JAR, Tar, and Zip distributions
#   2. Desktop: Runnable Uber JAR, Standalone Linux Distributable (.tar.gz)
#   3. Linux Package Managers: Debian (.deb) and Arch Linux (.pkg.tar.zst) packages
#   4. Android: Signed Release APK and Debug APK
#
# Automated features:
#   - Detects / installs Android runtime (cmdline-tools, platforms, build-tools)
#   - Agrees to all Android SDK licenses automatically
#   - Detects Android signing key; prompts to generate one if missing and updates .gitignore
#   - Consolidates all built artifacts into ./dist with SHA-256 checksums
# ==============================================================================

set -eo pipefail

# Color palette for terminal output
BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
BLUE="\033[0;34m"
NC="\033[0m" # No Color

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
step()    { echo -e "\n${BOLD}${BLUE}==>${NC} ${BOLD}$*${NC}"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="${PROJECT_ROOT}/dist"
APP_VERSION="1.0.0"

echo -e "${BOLD}${CYAN}"
echo "======================================================================"
echo "           QUOTES CONNECT — UNIVERSAL ARTIFACT BUILDER"
echo "        Algorithm-Free Sovereign Knowledge Graph Platform"
echo "======================================================================"
echo -e "${NC}"

# ------------------------------------------------------------------------------
# 1. Environment & Java Discovery
# ------------------------------------------------------------------------------
step "Step 1: Validating Java Development Kit (JDK)"

# Discover Java runtime dynamically without hardcoding any user or machine paths
if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    # 1. Check if 'java' binary is on PATH
    if command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
        RESOLVED_JAVA="$(readlink -f "$JAVA_BIN" 2>/dev/null || realpath "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")"
        CANDIDATE_HOME="$(dirname "$(dirname "$RESOLVED_JAVA")")"
        if [ -x "${CANDIDATE_HOME}/bin/java" ]; then
            export JAVA_HOME="${CANDIDATE_HOME}"
        fi
    fi

    # 2. Check SDKMAN active candidate via dynamic $HOME
    if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
        if [ -n "$HOME" ] && [ -d "$HOME/.sdkman/candidates/java/current" ] && [ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]; then
            export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
        fi
    fi

    # 3. Check macOS java_home helper if present
    if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
        if [ -x "/usr/libexec/java_home" ]; then
            MAC_JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
            if [ -n "$MAC_JAVA_HOME" ] && [ -x "${MAC_JAVA_HOME}/bin/java" ]; then
                export JAVA_HOME="$MAC_JAVA_HOME"
            fi
        fi
    fi

    # 4. Check common Linux/Unix JDK locations
    if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
        COMMON_JDK_DIRS=(
            "/usr/lib/jvm/default-runtime"
            "/usr/lib/jvm/default"
            "/usr/lib/jvm/java-21-openjdk"
            "/usr/lib/jvm/java-17-openjdk"
            "/usr/lib/jvm/java-21-openjdk-amd64"
            "/usr/lib/jvm/java-17-openjdk-amd64"
            "/usr/lib/jvm/zulu-21"
            "/usr/lib/jvm/temurin-21"
            "/opt/homebrew/opt/openjdk@21"
            "/opt/homebrew/opt/openjdk@17"
            "/usr/local/opt/openjdk@21"
        )
        for cand in "${COMMON_JDK_DIRS[@]}"; do
            if [ -d "$cand" ] && [ -x "$cand/bin/java" ]; then
                export JAVA_HOME="$cand"
                break
            fi
        done
    fi

    # 5. Check user JetBrains / Toolbox / asdf installed JDKs
    if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
        if [ -n "$HOME" ] && [ -d "$HOME/.jdks" ]; then
            for cand in "$HOME/.jdks"/*; do
                if [ -d "$cand" ] && [ -x "$cand/bin/java" ]; then
                    export JAVA_HOME="$cand"
                    break
                fi
            done
        fi
    fi
fi

# If JDK is still missing, warn clearly with install instructions and exit
if [ -z "$JAVA_HOME" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    echo ""
    error "Java Development Kit (JDK 17+) is required but was not found on your system."
    warn "JAVA_HOME is not set and no compatible Java runtime could be detected on PATH or standard locations."
    echo ""
    info "To install a suitable JDK (JDK 21 recommended):"
    echo "  - Arch Linux / CachyOS:  sudo pacman -S jdk21-openjdk"
    echo "  - Debian / Ubuntu:       sudo apt install openjdk-21-jdk"
    echo "  - Fedora / RHEL:         sudo dnf install java-21-openjdk-devel"
    echo "  - Alpine Linux:          sudo apk add openjdk21"
    echo "  - macOS (Homebrew):      brew install openjdk@21"
    echo "  - SDKMAN!:               sdk install java 21.0.3-tem"
    echo ""
    info "Or manually set JAVA_HOME before running this script:"
    echo "  export JAVA_HOME=/path/to/your/jdk"
    echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    echo "  ./build-all-artifacts.sh"
    echo ""
    exit 1
fi

export PATH="${JAVA_HOME}/bin:${PATH}"
info "Using JAVA_HOME: ${JAVA_HOME}"
JAVA_VERSION_OUTPUT="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)"
info "Java version: ${JAVA_VERSION_OUTPUT}"

# Check Java major version is >= 17
JAVA_MAJOR=$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{if ($1 == "1") print $2; else print $1;}')
if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt 17 ] 2>/dev/null; then
    warn "Detected Java version ${JAVA_MAJOR}. Kotlin Multiplatform and Gradle recommend JDK 17 or 21."
fi

if [ ! -x "${JAVA_HOME}/bin/keytool" ]; then
    error "keytool not found in ${JAVA_HOME}/bin. A full JDK (not a stripped JRE) is required to sign release packages."
    exit 1
fi

# ------------------------------------------------------------------------------
# 2. Android Runtime & SDK Setup
# ------------------------------------------------------------------------------
step "Step 2: Checking Android Runtime and Command-Line Tools"

# Detect if an Android SDK already exists on the system
EXISTING_SDK=""
CANDIDATE_SDK_DIRS=()
[ -n "$ANDROID_HOME" ] && CANDIDATE_SDK_DIRS+=("$ANDROID_HOME")
[ -n "$ANDROID_SDK_ROOT" ] && CANDIDATE_SDK_DIRS+=("$ANDROID_SDK_ROOT")

if [ -f "${PROJECT_ROOT}/local.properties" ]; then
    SDK_FROM_PROP="$(grep -E '^sdk\.dir=' "${PROJECT_ROOT}/local.properties" 2>/dev/null | cut -d'=' -f2- | tr -d '\r' || true)"
    [ -n "$SDK_FROM_PROP" ] && CANDIDATE_SDK_DIRS+=("$SDK_FROM_PROP")
fi

if [ -n "$HOME" ]; then
    CANDIDATE_SDK_DIRS+=(
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "$HOME/.android-sdk"
    )
fi
CANDIDATE_SDK_DIRS+=(
    "/opt/android-sdk"
    "/usr/lib/android-sdk"
    "${PROJECT_ROOT}/.android-sdk"
)

for cand in "${CANDIDATE_SDK_DIRS[@]}"; do
    if [ -d "$cand" ] && { [ -d "$cand/platforms" ] || [ -d "$cand/cmdline-tools" ] || [ -d "$cand/platform-tools" ] || [ -d "$cand/build-tools" ]; }; then
        EXISTING_SDK="$cand"
        break
    fi
done

if [ -n "$EXISTING_SDK" ]; then
    export ANDROID_HOME="$EXISTING_SDK"
    info "Found existing Android SDK at: ${ANDROID_HOME}"
else
    # Only auto-install Android SDK if completely absent
    warn "No Android SDK detected on this system. Initiating automatic setup..."
    if [ -n "$HOME" ] && mkdir -p "$HOME/Android/Sdk" 2>/dev/null; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        export ANDROID_HOME="${PROJECT_ROOT}/.android-sdk"
    fi
    mkdir -p "$ANDROID_HOME"
    info "Target Android SDK Directory: ${ANDROID_HOME}"
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
echo "sdk.dir=${ANDROID_HOME}" > "${PROJECT_ROOT}/local.properties"

# Locate or install sdkmanager
find_sdkmanager() {
    if [ -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
    elif [ -x "${ANDROID_HOME}/cmdline-tools/bin/sdkmanager" ]; then
        echo "${ANDROID_HOME}/cmdline-tools/bin/sdkmanager"
    elif [ -x "${ANDROID_HOME}/tools/bin/sdkmanager" ]; then
        echo "${ANDROID_HOME}/tools/bin/sdkmanager"
    else
        local found_any
        found_any="$(find "${ANDROID_HOME}/cmdline-tools" -maxdepth 3 -type f -name sdkmanager -perm -u+x 2>/dev/null | head -n 1 || true)"
        if [ -n "$found_any" ]; then
            echo "$found_any"
        elif command -v sdkmanager >/dev/null 2>&1; then
            command -v sdkmanager
        fi
    fi
}

SDKMANAGER="$(find_sdkmanager)"

# Auto-install cmdline-tools only if sdkmanager is absent
if [ -z "$SDKMANAGER" ] || [ ! -x "$SDKMANAGER" ]; then
    warn "Android Command-Line Tools (sdkmanager) absent. Downloading from Google CDN..."
    if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
        error "curl and unzip are required to install Android SDK tools."
        warn "Please install curl and unzip or install the Android SDK manually."
        exit 1
    fi

    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    TEMP_ZIP="/tmp/android_cmdline_tools_$$.zip"
    TEMP_EXTRACT="/tmp/android_cmdline_extract_$$"

    info "Downloading Android commandline tools archive..."
    curl -fsSL -o "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"
    
    mkdir -p "$TEMP_EXTRACT"
    unzip -q -o "$TEMP_ZIP" -d "$TEMP_EXTRACT"
    
    mkdir -p "${ANDROID_HOME}/cmdline-tools"
    rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
    mv "${TEMP_EXTRACT}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
    
    rm -f "$TEMP_ZIP"
    rm -rf "$TEMP_EXTRACT"
    SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
    success "Installed Android Command-Line Tools to ${ANDROID_HOME}/cmdline-tools/latest"
fi

# Automatically accept all Android SDK licenses
info "Accepting all Android SDK licenses..."
yes 2>/dev/null | "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" --licenses >/dev/null 2>&1 || true

# Ensure required platform and build-tools are present
PLATFORMS_DIR="${ANDROID_HOME}/platforms"
mkdir -p "$PLATFORMS_DIR"

if [ ! -d "${PLATFORMS_DIR}/android-37.0" ] && [ ! -d "${PLATFORMS_DIR}/android-37" ]; then
    info "Platform android-37 absent. Installing Android SDK Platform 37.0..."
    "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" "platforms;android-37.0" || true
fi

# Ensure android-37 symlink exists for AGP compatibility
if [ -d "${PLATFORMS_DIR}/android-37.0" ] && [ ! -e "${PLATFORMS_DIR}/android-37" ]; then
    ln -sf "${PLATFORMS_DIR}/android-37.0" "${PLATFORMS_DIR}/android-37"
    info "Linked android-37 -> android-37.0 for Gradle compatibility."
fi

# Ensure platform-tools is present
if [ ! -d "${ANDROID_HOME}/platform-tools" ]; then
    info "platform-tools absent. Installing Android platform-tools..."
    "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" "platform-tools" || true
fi

success "Android runtime verified and ready."

# ------------------------------------------------------------------------------
# 3. Android Signing Key Verification & Generation
# ------------------------------------------------------------------------------
step "Step 3: Checking Android Release Signing Key"

KEYSTORE_FILE="${PROJECT_ROOT}/release.keystore"
PROPS_FILE="${PROJECT_ROOT}/keystore.properties"

# Check if .gitignore covers keystores and properties
GITIGNORE_FILE="${PROJECT_ROOT}/.gitignore"
ensure_gitignore() {
    local entry="$1"
    if [ -f "$GITIGNORE_FILE" ]; then
        if ! grep -q -x "$entry" "$GITIGNORE_FILE" 2>/dev/null && ! grep -q "^$entry" "$GITIGNORE_FILE" 2>/dev/null; then
            echo "$entry" >> "$GITIGNORE_FILE"
            info "Added '$entry' to .gitignore"
        fi
    fi
}
ensure_gitignore "*.keystore"
ensure_gitignore "*.jks"
ensure_gitignore "keystore.properties"
ensure_gitignore "dist/"
ensure_gitignore ".android-sdk/"

if [ ! -f "$KEYSTORE_FILE" ]; then
    warn "No Android release signing keystore found at ${KEYSTORE_FILE}."
    
    DO_GENERATE="Y"
    if [ -t 0 ]; then
        echo -e "${YELLOW}"
        read -r -p "[?] Would you like to generate a new Android release signing key now? [Y/n]: " USER_GEN_CHOICE
        echo -e "${NC}"
        USER_GEN_CHOICE="${USER_GEN_CHOICE:-Y}"
        if [[ ! "$USER_GEN_CHOICE" =~ ^[Yy]$ ]]; then
            DO_GENERATE="N"
        fi
    fi

    if [ "$DO_GENERATE" = "Y" ]; then
        KEY_ALIAS="quotes-connect"
        STORE_PASS=""
        ORG_NAME="Quotes Connect"

        if [ -t 0 ]; then
            read -r -p "Enter Key Alias [default: ${KEY_ALIAS}]: " INPUT_ALIAS
            KEY_ALIAS="${INPUT_ALIAS:-$KEY_ALIAS}"
            
            read -r -s -p "Enter Keystore Password (press ENTER to auto-generate a secure random 32-char key): " INPUT_PASS
            echo ""
            STORE_PASS="$INPUT_PASS"

            read -r -p "Enter Author / Organization Name [default: ${ORG_NAME}]: " INPUT_ORG
            ORG_NAME="${INPUT_ORG:-$ORG_NAME}"
        fi

        if [ -z "$STORE_PASS" ]; then
            STORE_PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
            info "Generated random strong alphanumeric keystore password: ${STORE_PASS}"
        fi
        KEY_PASS="$STORE_PASS"

        info "Generating RSA 2048-bit release keystore with 10,000 days validity..."
        "${JAVA_HOME}/bin/keytool" -genkeypair -v \
            -keystore "$KEYSTORE_FILE" \
            -alias "$KEY_ALIAS" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -storepass "$STORE_PASS" \
            -keypass "$KEY_PASS" \
            -dname "CN=${ORG_NAME}, OU=QuotesConnect, O=${ORG_NAME}, L=Decentralized, ST=Global, C=US"

        cat <<EOF > "$PROPS_FILE"
storeFile=release.keystore
storePassword=${STORE_PASS}
keyAlias=${KEY_ALIAS}
keyPassword=${KEY_PASS}
EOF
        chmod 600 "$KEYSTORE_FILE" "$PROPS_FILE"
        success "Created release keystore at: ${KEYSTORE_FILE}"
        success "Created keystore properties at: ${PROPS_FILE}"
    else
        warn "Skipping keystore creation. Release build may be unsigned or fallback to debug."
    fi
else
    success "Found existing signing keystore: ${KEYSTORE_FILE}"
    if [ ! -f "$PROPS_FILE" ]; then
        warn "keystore.properties missing. Creating default reference."
        cat <<EOF > "$PROPS_FILE"
storeFile=release.keystore
storePassword=
keyAlias=quotes-connect
keyPassword=
EOF
    fi
fi

# ------------------------------------------------------------------------------
# 4. Prepare Distribution Directories
# ------------------------------------------------------------------------------
step "Step 4: Preparing Output Directory (${DIST_DIR})"
rm -rf "$DIST_DIR"
mkdir -p "${DIST_DIR}/server" "${DIST_DIR}/desktop" "${DIST_DIR}/android"

# ------------------------------------------------------------------------------
# 5. Build Server Artifacts
# ------------------------------------------------------------------------------
step "Step 5: Building Server Artifacts (Fat JAR & Distribution Tarballs)"

"${PROJECT_ROOT}/gradlew" -Dorg.gradle.java.home="${JAVA_HOME}" :server:buildFatJar :server:distTar :server:distZip --no-daemon

SERVER_FAT_JAR="${PROJECT_ROOT}/server/build/libs/server-all.jar"
if [ -f "$SERVER_FAT_JAR" ]; then
    cp "$SERVER_FAT_JAR" "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.jar"
    success "Built Server Fat JAR: dist/server/quotes-connect-server-${APP_VERSION}.jar"
fi

if [ -f "${PROJECT_ROOT}/server/build/distributions/server-${APP_VERSION}.tar" ]; then
    cp "${PROJECT_ROOT}/server/build/distributions/server-${APP_VERSION}.tar" "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.tar"
    success "Built Server Distribution Tar: dist/server/quotes-connect-server-${APP_VERSION}.tar"
fi

if [ -f "${PROJECT_ROOT}/server/build/distributions/server-${APP_VERSION}.zip" ]; then
    cp "${PROJECT_ROOT}/server/build/distributions/server-${APP_VERSION}.zip" "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.zip"
    success "Built Server Distribution Zip: dist/server/quotes-connect-server-${APP_VERSION}.zip"
fi

# ------------------------------------------------------------------------------
# 6. Build Desktop Application Artifacts
# ------------------------------------------------------------------------------
step "Step 6: Building Desktop Application (Uber JAR & Standalone Distributable)"

"${PROJECT_ROOT}/gradlew" -Dorg.gradle.java.home="${JAVA_HOME}" :app:desktopApp:packageReleaseUberJarForCurrentOS :app:desktopApp:createReleaseDistributable --no-daemon

DESKTOP_UBER_JAR="${PROJECT_ROOT}/app/desktopApp/build/compose/jars/connects.quotes-linux-x64-${APP_VERSION}-release.jar"
if [ -f "$DESKTOP_UBER_JAR" ]; then
    cp "$DESKTOP_UBER_JAR" "${DIST_DIR}/desktop/quotes-connect-desktop-${APP_VERSION}.jar"
    success "Built Desktop Runnable Uber JAR: dist/desktop/quotes-connect-desktop-${APP_VERSION}.jar"
fi

APP_DIR="${PROJECT_ROOT}/app/desktopApp/build/compose/binaries/main-release/app/connects.quotes"
if [ -d "$APP_DIR" ]; then
    info "Bundling Standalone Desktop Linux Distributable (.tar.gz)..."
    tar -czf "${DIST_DIR}/desktop/quotes-connect-desktop-${APP_VERSION}-linux-x64.tar.gz" -C "$(dirname "$APP_DIR")" connects.quotes
    success "Built Desktop Standalone Tarball: dist/desktop/quotes-connect-desktop-${APP_VERSION}-linux-x64.tar.gz"
fi

# ------------------------------------------------------------------------------
# 7. Build Linux Package Manager Binaries (Debian .deb & Arch Linux .pkg.tar.zst)
# ------------------------------------------------------------------------------
step "Step 7: Generating Package Manager Binaries (.deb & Arch .pkg.tar.zst)"

if [ -d "$APP_DIR" ]; then
    ICON_SRC="${APP_DIR}/lib/connects.quotes.png"
    if [ ! -f "$ICON_SRC" ] && [ -f "${PROJECT_ROOT}/app/desktopApp/src/main/resources/icon.png" ]; then
        ICON_SRC="${PROJECT_ROOT}/app/desktopApp/src/main/resources/icon.png"
    fi
    
    # ---------------- A. Debian Package (.deb) ----------------
    info "Assembling Debian package (.deb)..."
    DEB_STAGE="/tmp/deb_stage_$$"
    rm -rf "$DEB_STAGE"
    mkdir -p "${DEB_STAGE}/opt/quotes-connect"
    mkdir -p "${DEB_STAGE}/usr/bin"
    mkdir -p "${DEB_STAGE}/usr/share/applications"
    mkdir -p "${DEB_STAGE}/usr/share/pixmaps"
    mkdir -p "${DEB_STAGE}/DEBIAN"

    # Copy files
    cp -r "${APP_DIR}/"* "${DEB_STAGE}/opt/quotes-connect/"
    chmod 755 "${DEB_STAGE}/opt/quotes-connect/bin/connects.quotes"
    
    # Symlink
    ln -sf "/opt/quotes-connect/bin/connects.quotes" "${DEB_STAGE}/usr/bin/quotes-connect"

    # Desktop entry
    cat <<EOF > "${DEB_STAGE}/usr/share/applications/quotes-connect.desktop"
[Desktop Entry]
Name=Quotes Connect
GenericName=Knowledge Graph Platform
Comment=Algorithm-Free Sovereign Knowledge Graph Platform
Exec=/opt/quotes-connect/bin/connects.quotes
Icon=quotes-connect
Terminal=false
Type=Application
Categories=Office;Education;Utility;Network;
StartupWMClass=connects.quotes.MainKt
EOF
    chmod 644 "${DEB_STAGE}/usr/share/applications/quotes-connect.desktop"

    # Icon
    if [ -f "$ICON_SRC" ]; then
        cp "$ICON_SRC" "${DEB_STAGE}/usr/share/pixmaps/quotes-connect.png"
    fi

    # Control file
    INSTALLED_SIZE=$(du -sk "${DEB_STAGE}" | cut -f1)
    cat <<EOF > "${DEB_STAGE}/DEBIAN/control"
Package: quotes-connect
Version: ${APP_VERSION}
Section: utils
Priority: optional
Architecture: amd64
Installed-Size: ${INSTALLED_SIZE}
Maintainer: Quotes Connect Community <connects.quotes@local>
Homepage: https://github.com/quotes-connect
Description: Quotes Connect — Sovereign Knowledge Graph Platform
 Algorithm-free, sovereign, decentralized knowledge network.
 Provides interconnected passages in a pure Directed Acyclic Graph (DAG)
 with zero algorithmic recommendation engines and absolute author privacy.
EOF

    DEB_OUT="${DIST_DIR}/desktop/quotes-connect_${APP_VERSION}_amd64.deb"
    if command -v dpkg-deb >/dev/null 2>&1; then
        dpkg-deb --build "$DEB_STAGE" "$DEB_OUT" >/dev/null 2>&1
    else
        # Standard ar + tar format for systems without dpkg-deb (e.g. Arch, Fedora, Alpine)
        CONTROL_TAR="/tmp/control_tar_$$.tar.gz"
        DATA_TAR="/tmp/data_tar_$$.tar.gz"
        echo "2.0" > "${DEB_STAGE}/debian-binary"
        tar -czf "$CONTROL_TAR" -C "${DEB_STAGE}/DEBIAN" .
        tar -czf "$DATA_TAR" -C "${DEB_STAGE}" opt usr
        ar rcs "$DEB_OUT" "${DEB_STAGE}/debian-binary" "$CONTROL_TAR" "$DATA_TAR"
        rm -f "$CONTROL_TAR" "$DATA_TAR"
    fi
    rm -rf "$DEB_STAGE"
    success "Built Debian Package: dist/desktop/quotes-connect_${APP_VERSION}_amd64.deb"

    # ---------------- B. Arch Linux Package (.pkg.tar.zst) ----------------
    info "Assembling Arch Linux pacman package (.pkg.tar.zst)..."
    ARCH_STAGE="/tmp/arch_stage_$$"
    rm -rf "$ARCH_STAGE"
    mkdir -p "${ARCH_STAGE}/opt/quotes-connect"
    mkdir -p "${ARCH_STAGE}/usr/bin"
    mkdir -p "${ARCH_STAGE}/usr/share/applications"
    mkdir -p "${ARCH_STAGE}/usr/share/pixmaps"

    cp -r "${APP_DIR}/"* "${ARCH_STAGE}/opt/quotes-connect/"
    chmod 755 "${ARCH_STAGE}/opt/quotes-connect/bin/connects.quotes"
    ln -sf "/opt/quotes-connect/bin/connects.quotes" "${ARCH_STAGE}/usr/bin/quotes-connect"

    if [ -f "$ICON_SRC" ]; then
        cp "$ICON_SRC" "${ARCH_STAGE}/usr/share/pixmaps/quotes-connect.png"
    fi

    cat <<EOF > "${ARCH_STAGE}/usr/share/applications/quotes-connect.desktop"
[Desktop Entry]
Name=Quotes Connect
GenericName=Knowledge Graph Platform
Comment=Algorithm-Free Sovereign Knowledge Graph Platform
Exec=/opt/quotes-connect/bin/connects.quotes
Icon=quotes-connect
Terminal=false
Type=Application
Categories=Office;Education;Utility;Network;
StartupWMClass=connects.quotes.MainKt
EOF

    PKG_SIZE=$(du -sb "${ARCH_STAGE}" | cut -f1)
    BUILD_DATE=$(date +%s)
    cat <<EOF > "${ARCH_STAGE}/.PKGINFO"
pkgname = quotes-connect-bin
pkgbase = quotes-connect-bin
pkgver = ${APP_VERSION}-1
pkgdesc = Quotes Connect — Algorithm-Free Sovereign Knowledge Graph Platform
url = https://github.com/quotes-connect
builddate = ${BUILD_DATE}
packager = Quotes Connect Packager <connects.quotes@local>
size = ${PKG_SIZE}
arch = x86_64
license = custom
EOF

    ARCH_OUT="${DIST_DIR}/desktop/quotes-connect-bin-${APP_VERSION}-1-x86_64.pkg.tar.zst"
    if command -v zstd >/dev/null 2>&1; then
        tar --zstd -cf "$ARCH_OUT" -C "$ARCH_STAGE" .PKGINFO opt usr
        success "Built Arch Linux Package: dist/desktop/quotes-connect-bin-${APP_VERSION}-1-x86_64.pkg.tar.zst"
    else
        warn "zstd not found; skipping Arch Linux .pkg.tar.zst generation."
    fi
    rm -rf "$ARCH_STAGE"
fi

# ------------------------------------------------------------------------------
# 8. Build Android Application Artifacts
# ------------------------------------------------------------------------------
step "Step 8: Building Android Application (Release & Debug APKs)"

"${PROJECT_ROOT}/gradlew" -Dorg.gradle.java.home="${JAVA_HOME}" :app:androidApp:assembleRelease :app:androidApp:assembleDebug --no-daemon

RELEASE_APK="${PROJECT_ROOT}/app/androidApp/build/outputs/apk/release/androidApp-release.apk"
if [ -f "$RELEASE_APK" ]; then
    cp "$RELEASE_APK" "${DIST_DIR}/android/quotes-connect-${APP_VERSION}-release.apk"
    success "Built Android Release APK: dist/android/quotes-connect-${APP_VERSION}-release.apk"
else
    # Check for unsigned release if not signed
    RELEASE_UNSIGNED="${PROJECT_ROOT}/app/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk"
    if [ -f "$RELEASE_UNSIGNED" ]; then
        cp "$RELEASE_UNSIGNED" "${DIST_DIR}/android/quotes-connect-${APP_VERSION}-release-unsigned.apk"
        warn "Built Unsigned Release APK: dist/android/quotes-connect-${APP_VERSION}-release-unsigned.apk"
    fi
fi

DEBUG_APK="${PROJECT_ROOT}/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
if [ -f "$DEBUG_APK" ]; then
    cp "$DEBUG_APK" "${DIST_DIR}/android/quotes-connect-${APP_VERSION}-debug.apk"
    success "Built Android Debug APK: dist/android/quotes-connect-${APP_VERSION}-debug.apk"
fi

# ------------------------------------------------------------------------------
# 9. Generate Checksums & Print Manifest Summary
# ------------------------------------------------------------------------------
step "Step 9: Generating SHA-256 Checksums and Manifest"

cd "${DIST_DIR}"
find . -type f ! -name "SHA256SUMS" -print0 | sort -z | xargs -0 sha256sum > "${DIST_DIR}/SHA256SUMS"
cd "${PROJECT_ROOT}"

echo -e "\n${BOLD}${GREEN}======================================================================${NC}"
echo -e "${BOLD}${GREEN}                   BUILD SUCCESSFUL — ARTIFACT SUMMARY                ${NC}"
echo -e "${BOLD}${GREEN}======================================================================${NC}"
printf "%-12s %-48s %-12s\n" "CATEGORY" "ARTIFACT FILE" "SIZE"
echo "----------------------------------------------------------------------"

format_row() {
    local cat="$1"
    local file="$2"
    if [ -f "$file" ]; then
        local size
        size=$(ls -lh "$file" | awk '{print $5}')
        local name
        name="$(basename "$file")"
        printf "%-12s %-48s %-12s\n" "$cat" "$name" "$size"
    fi
}

format_row "Server"   "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.jar"
format_row "Server"   "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.tar"
format_row "Server"   "${DIST_DIR}/server/quotes-connect-server-${APP_VERSION}.zip"
format_row "Desktop"  "${DIST_DIR}/desktop/quotes-connect-desktop-${APP_VERSION}.jar"
format_row "Desktop"  "${DIST_DIR}/desktop/quotes-connect-desktop-${APP_VERSION}-linux-x64.tar.gz"
format_row "Debian"   "${DIST_DIR}/desktop/quotes-connect_${APP_VERSION}_amd64.deb"
format_row "Arch"     "${DIST_DIR}/desktop/quotes-connect-bin-${APP_VERSION}-1-x86_64.pkg.tar.zst"
format_row "Android"  "${DIST_DIR}/android/quotes-connect-${APP_VERSION}-release.apk"
format_row "Android"  "${DIST_DIR}/android/quotes-connect-${APP_VERSION}-debug.apk"
echo "----------------------------------------------------------------------"
info "All artifacts saved to: ${DIST_DIR}"
info "Checksums catalog:      ${DIST_DIR}/SHA256SUMS"
echo ""
