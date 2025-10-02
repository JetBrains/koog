#!/usr/bin/env bash
set -euo pipefail

# Always operate relative to this script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${SCRIPT_DIR}/a2a-tck"

if [ ! -d "${REPO_DIR}" ]; then
  echo "[run_tck] Expected directory not found: ${REPO_DIR}"
  echo "[run_tck] Please run setup_tck.sh first to clone and prepare A2A testing kit project."
  exit 1
fi

cd "${REPO_DIR}"

# Delegate all CLI parameters to the underlying script
exec uv run ./run_tck.py "$@"
