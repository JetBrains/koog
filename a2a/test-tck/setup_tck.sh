#!/usr/bin/env bash

set -euo pipefail

# Resolve the directory where this script resides to always operate relative to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${SCRIPT_DIR}/a2a-tck"
REPO_URL="https://github.com/a2aproject/a2a-tck.git"
COMMIT_HASH="263b9cfaf16a554bdfb166a7ba5b67716e946349" # A2A version 1.0.x

checkout_pinned_commit() {
  git -C "${REPO_DIR}" config advice.detachedHead false
  git -C "${REPO_DIR}" fetch --depth=1 origin "${COMMIT_HASH}"
  git -C "${REPO_DIR}" checkout FETCH_HEAD
}

# 1) Clone a2a-tck, or move an existing clone to the pinned commit
if [ -d "${REPO_DIR}" ]; then
  CURRENT_COMMIT="$(git -C "${REPO_DIR}" rev-parse HEAD)"
  if [ "${CURRENT_COMMIT}" = "${COMMIT_HASH}" ]; then
    echo "[setup_tck] 'a2a-tck' already exists at ${REPO_DIR} on the pinned commit ${COMMIT_HASH}."
  else
    echo "[setup_tck] 'a2a-tck' at ${REPO_DIR} is on ${CURRENT_COMMIT}, switching to the pinned commit ${COMMIT_HASH}"
    checkout_pinned_commit
  fi
else
  echo "[setup_tck] 'a2a-tck' directory not found in ${SCRIPT_DIR}."
  echo "[setup_tck] Cloning repository into ${REPO_DIR} at commit ${COMMIT_HASH}"

  git clone "${REPO_URL}" "${REPO_DIR}" --depth=1
  checkout_pinned_commit
fi

# 2) Always run uv sync in the repo directory
echo "[setup_tck] Running 'uv sync' in ${REPO_DIR}..."
(
  cd "${REPO_DIR}"
  uv sync --all-packages --all-extras
)
echo "[setup_tck] Done."
