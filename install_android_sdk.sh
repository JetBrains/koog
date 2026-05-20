#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

SDK_ROOT="$ANDROID_HOME"
CMDLINE_TOOLS_DIR="$SDK_ROOT/cmdline-tools"
LATEST_DIR="$CMDLINE_TOOLS_DIR/latest"

echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

mkdir -p "$CMDLINE_TOOLS_DIR"

if [ ! -x "$LATEST_DIR/bin/sdkmanager" ]; then
  echo "Android SDK command-line tools are missing. Installing..."

  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT

  cd "$TMP_DIR"

  curl --fail --location --show-error --output commandlinetools-mac.zip \
    "https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip"

  unzip -q commandlinetools-mac.zip

  rm -rf "$LATEST_DIR"
  mkdir -p "$LATEST_DIR"

  # The archive contains a top-level "cmdline-tools" directory.
  mv cmdline-tools/* "$LATEST_DIR/"
else
  echo "Android SDK command-line tools already installed."
fi

export PATH="$LATEST_DIR/bin:$SDK_ROOT/platform-tools:$PATH"

echo "sdkmanager version:"
sdkmanager --version

echo "Accepting Android SDK licenses..."
set +o pipefail
yes | sdkmanager --licenses
licenses_status=${PIPESTATUS[1]}
set -o pipefail

if [ "$licenses_status" -ne 0 ]; then
  echo "sdkmanager --licenses failed with exit code $licenses_status" >&2
  exit "$licenses_status"
fi

echo "Installing Android SDK packages..."
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

echo "Installed Android SDK packages:"
sdkmanager --list_installed
