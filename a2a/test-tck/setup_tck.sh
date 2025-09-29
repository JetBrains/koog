#!/usr/bin/env bash

set -euo pipefail

# Resolve the directory where this script resides to always operate relative to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${SCRIPT_DIR}/a2a-tck"
REPO_URL="https://github.com/a2aproject/a2a-tck.git"

# 1) Check if a2a-tck already exists
if [ -d "${REPO_DIR}" ]; then
  echo "[setup_tck] 'a2a-tck' directory already exists at: ${REPO_DIR}"
  echo "[setup_tck] Will skip cloning but still run 'uv sync'."
else
  echo "[setup_tck] 'a2a-tck' directory not found in ${SCRIPT_DIR}."
  echo "[setup_tck] Cloning repository into: ${REPO_DIR}"
  git clone "${REPO_URL}" "${REPO_DIR}" --depth=1
fi

# 2) Always run uv sync in the repo directory
echo "[setup_tck] Running 'uv sync' in ${REPO_DIR}..."
(
  cd "${REPO_DIR}"
  uv sync --all-packages --all-extras
)
echo "[setup_tck] Done."
