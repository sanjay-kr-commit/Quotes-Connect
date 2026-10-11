#!/usr/bin/env bash
# ==============================================================================
# Quotes Connect — Clean Script
# ==============================================================================
# Performs a thorough cleanup of the project:
#   1. Runs Gradle clean (:clean) with --no-configuration-cache
#   2. Deletes generated distribution artifacts (dist/, artifacts/)
#   3. Deletes all module build directories (build/, **/build/)
#   4. Deletes compiler caches, staging directories, and temporary files
#
# Flags:
#   --all / -a      : Deep clean including local databases, dev keystores, and .gradle cache
#   --db            : Clean local SQLite databases (*.db, *.db-journal, etc.)
#   --cache         : Clean .gradle build cache directory
#   --dry-run       : Show items that would be deleted without removing them
#   --help / -h     : Show this help message
# ==============================================================================

set -eo pipefail

# Terminal colors
BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
BLUE="\033[0;34m"
NC="\033[0m"

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
step()    { echo -e "\n${BOLD}${BLUE}==>${NC} ${BOLD}$*${NC}"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="${PROJECT_ROOT}/dist"
ARTIFACTS_DIR="${PROJECT_ROOT}/artifacts"

# Command line options
CLEAN_ALL=false
CLEAN_DB=false
CLEAN_CACHE=false
DRY_RUN=false

show_help() {
    echo -e "${BOLD}Quotes Connect Clean Script${NC}"
    echo ""
    echo "Usage:"
    echo "  ./clean.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -a, --all        Deep clean: removes build dirs, dist artifacts, local SQLite"
    echo "                   databases, developer dev keystore, and .gradle cache."
    echo "  --db             Clean local SQLite database files (*.db, *.db-journal, *.db-wal)."
    echo "  --cache          Clean the local .gradle build cache directory."
    echo "  --dry-run        Print actions that would be performed without executing them."
    echo "  -h, --help       Show this help message."
    echo ""
    echo "Examples:"
    echo "  ./clean.sh             # Standard clean: runs gradle clean and removes dist/ + build dirs"
    echo "  ./clean.sh --all       # Deep clean everything including databases and caches"
    echo "  ./clean.sh --db        # Clean build artifacts and remove SQLite database files"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--all)
            CLEAN_ALL=true
            CLEAN_DB=true
            CLEAN_CACHE=true
            shift
            ;;
        --db)
            CLEAN_DB=true
            shift
            ;;
        --cache)
            CLEAN_CACHE=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            error "Unknown argument: $1"
            show_help
            exit 1
            ;;
    esac
done

echo -e "${BOLD}${CYAN}"
echo "======================================================================"
echo "                 QUOTES CONNECT — CLEAN SCRIPT"
echo "======================================================================"
echo -e "${NC}"

if [ "$DRY_RUN" = true ]; then
    warn "Running in DRY RUN mode. No files will be modified or deleted."
fi

# ------------------------------------------------------------------------------
# 1. Environment & Java Discovery for Gradle
# ------------------------------------------------------------------------------
step "Step 1: Discovering Java Environment"

if [ -z "$JAVA_HOME" ]; then
    if [ -n "$HOME" ] && [ -d "$HOME/.sdkman/candidates/java/current" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    elif command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
        RESOLVED_JAVA="$(readlink -f "$JAVA_BIN" 2>/dev/null || realpath "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")"
        export JAVA_HOME="$(dirname "$(dirname "$RESOLVED_JAVA")")"
    fi
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
    info "Using JAVA_HOME: ${JAVA_HOME}"
else
    warn "JAVA_HOME could not be detected. Will rely on PATH java."
fi

# ------------------------------------------------------------------------------
# 2. Run Gradle Clean
# ------------------------------------------------------------------------------
step "Step 2: Executing Gradle Clean Task"

if [ -x "${PROJECT_ROOT}/gradlew" ]; then
    if [ "$DRY_RUN" = true ]; then
        info "[DRY-RUN] Would run: ./gradlew clean --no-configuration-cache"
    else
        local gradle_args=()
        if [ -n "$JAVA_HOME" ]; then
            gradle_args+=("-Dorg.gradle.java.home=${JAVA_HOME}")
        fi
        if "${PROJECT_ROOT}/gradlew" "${gradle_args[@]}" clean --no-configuration-cache --no-daemon; then
            success "Gradle clean task completed successfully."
        else
            warn "Gradle clean reported an issue; proceeding with manual file deletion..."
        fi
    fi
else
    warn "Gradle wrapper (${PROJECT_ROOT}/gradlew) not found or not executable."
fi

# ------------------------------------------------------------------------------
# 3. Remove Generated Artifacts (dist/ and artifacts/)
# ------------------------------------------------------------------------------
step "Step 3: Removing Generated Distribution Artifacts"

delete_path() {
    local target="$1"
    local desc="$2"

    if [ -e "$target" ] || [ -d "$target" ]; then
        if [ "$DRY_RUN" = true ]; then
            info "[DRY-RUN] Would remove: ${target} (${desc})"
        else
            local size
            size=$(du -sh "$target" 2>/dev/null | awk '{print $1}' || echo "N/A")
            rm -rf "$target"
            success "Removed: ${target} [${size}] (${desc})"
        fi
    fi
}

delete_path "${DIST_DIR}" "Distribution binaries, APKs, JARs, packages"
delete_path "${ARTIFACTS_DIR}" "Artifacts directory"

# ------------------------------------------------------------------------------
# 4. Remove All Build Directories & Caches
# ------------------------------------------------------------------------------
step "Step 4: Ensuring All Module Build Directories Are Cleaned"

MODULE_BUILDS=(
    "${PROJECT_ROOT}/build"
    "${PROJECT_ROOT}/core/build"
    "${PROJECT_ROOT}/server/build"
    "${PROJECT_ROOT}/app/shared/build"
    "${PROJECT_ROOT}/app/desktopApp/build"
    "${PROJECT_ROOT}/app/androidApp/build"
    "${PROJECT_ROOT}/app/webApp/build"
    "${PROJECT_ROOT}/.kotlin"
    "${PROJECT_ROOT}/kotlin-js-store"
    "${PROJECT_ROOT}/.cxx"
    "${PROJECT_ROOT}/.externalNativeBuild"
)

for bdir in "${MODULE_BUILDS[@]}"; do
    delete_path "$bdir" "Build / Compiler cache output"
done

# Clean any lingering temporary staging directories in /tmp
for tmpdir in /tmp/quotes-connect-*; do
    if [ -d "$tmpdir" ]; then
        delete_path "$tmpdir" "Temporary packaging stage"
    fi
done

# ------------------------------------------------------------------------------
# 5. Optional / Deep Clean (Databases, Dev Keystore, Gradle Cache)
# ------------------------------------------------------------------------------
if [ "$CLEAN_DB" = true ]; then
    step "Step 5: Cleaning Local SQLite Database Files"
    
    # Search and remove local SQLite database files created during development/runs
    while IFS= read -r -d '' dbfile; do
        delete_path "$dbfile" "Local SQLite database file"
    done < <(find "${PROJECT_ROOT}" -maxdepth 3 \( -name "*.db" -o -name "*.db-journal" -o -name "*.db-wal" -o -name "*.db-shm" \) -print0 2>/dev/null)
fi

if [ "$CLEAN_ALL" = true ]; then
    step "Step 6: Cleaning Developer Key Stores & Session State"
    delete_path "${PROJECT_ROOT}/.quotes_connect" "Developer runtime storage & dev TLS certificate"
fi

if [ "$CLEAN_CACHE" = true ]; then
    step "Step 7: Cleaning Gradle Cache (.gradle)"
    delete_path "${PROJECT_ROOT}/.gradle" "Local Gradle project cache"
fi

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo ""
echo -e "${BOLD}${GREEN}======================================================================${NC}"
if [ "$DRY_RUN" = true ]; then
    echo -e "${BOLD}${YELLOW}                   DRY RUN COMPLETED SUCCESSFULLY                     ${NC}"
else
    echo -e "${BOLD}${GREEN}                   CLEAN COMPLETED SUCCESSFULLY                       ${NC}"
fi
echo -e "${BOLD}${GREEN}======================================================================${NC}"
info "Project workspace is clean and ready for a fresh build."
echo ""
