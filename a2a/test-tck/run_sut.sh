#!/usr/bin/env bash
set -euo pipefail

# Always operate relative to this script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${ROOT_DIR}"

# Run the SUT via Gradle; delegate any extra CLI args to Gradle
exec ./gradlew :a2a:test-tck:a2a-test-server-tck:run "$@"
