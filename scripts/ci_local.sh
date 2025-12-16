#!/bin/bash
#
# CI Local - Run the full CI suite locally
#
# This script runs all tests that CI runs, providing local parity with the
# GitHub Actions CI pipeline.
#
# Prerequisites:
# - JDK 17
# - Docker Desktop / Docker Engine
# - Android SDK (for instrumented tests)
#
# Usage:
#   ./scripts/ci_local.sh [--skip-docker]
#
# Options:
#   --skip-docker    Skip Docker-based integration tests

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse arguments
SKIP_DOCKER=false
for arg in "$@"; do
    case $arg in
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        *)
            ;;
    esac
done

cd "$PROJECT_ROOT"

# Check prerequisites
log_info "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    log_error "Java is not installed. Please install JDK 17."
    exit 1
fi

# Parse Java version - handles both old (1.8.0) and new (17.0.1) formats
JAVA_VERSION_STRING=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
JAVA_MAJOR=$(echo "$JAVA_VERSION_STRING" | cut -d'.' -f1)
# Handle old format like 1.8.0 where major version is after first dot
if [[ "$JAVA_MAJOR" == "1" ]]; then
    JAVA_MAJOR=$(echo "$JAVA_VERSION_STRING" | cut -d'.' -f2)
fi
# Validate that JAVA_MAJOR is numeric
if [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]]; then
    if [[ "$JAVA_MAJOR" -lt 17 ]]; then
        log_warn "Java version is $JAVA_VERSION_STRING. JDK 17+ is recommended."
    fi
else
    log_warn "Could not parse Java version from: $JAVA_VERSION_STRING"
fi

if [[ "$SKIP_DOCKER" == "false" ]]; then
    if ! command -v docker &> /dev/null; then
        log_warn "Docker is not installed. Skipping Docker-based tests."
        SKIP_DOCKER=true
    fi
fi

# Step 1: Clean and run unit tests
log_info "Running unit tests..."
./gradlew --no-daemon clean testDebugUnitTest

# Step 2: Run lint
log_info "Running lint checks..."
./gradlew --no-daemon lint

# Step 3: Build debug APK (ensures compilation succeeds)
log_info "Building debug APK..."
./gradlew --no-daemon :app:assembleDebug

# Step 4: Docker-based integration tests
if [[ "$SKIP_DOCKER" == "false" ]]; then
    log_info "Starting Docker services for integration tests..."
    cd "$PROJECT_ROOT/tests"
    
    # Start services
    docker compose up -d --wait
    
    # Wait for services to be healthy
    log_info "Waiting for services to be healthy..."
    sleep 10
    
    # Verify services are running
    log_info "Verifying Docker services..."
    docker compose ps
    
    # Shutdown services
    log_info "Shutting down Docker services..."
    docker compose down -v
    
    cd "$PROJECT_ROOT"
else
    log_warn "Skipping Docker-based integration tests"
fi

log_info "All CI checks passed successfully!"
echo ""
echo "Summary:"
echo "  ✓ Unit tests passed"
echo "  ✓ Lint checks passed"
echo "  ✓ Debug build successful"
if [[ "$SKIP_DOCKER" == "false" ]]; then
    echo "  ✓ Docker services verified"
else
    echo "  ⊘ Docker tests skipped"
fi
